package pe.edu.utec.atlasrambackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.dto.AntibiogramResponseDTO;
import pe.edu.utec.atlasrambackend.dto.DistrictResistanceResponseDTO;
import pe.edu.utec.atlasrambackend.dto.ReferralAntibiogramResponseDTO;
import pe.edu.utec.atlasrambackend.dto.ReferralFacilityDTO;
import pe.edu.utec.atlasrambackend.dto.ReferralFacilityRow;
import pe.edu.utec.atlasrambackend.dto.TrendResponseDTO;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.interpretation.ParsedMic;
import pe.edu.utec.atlasrambackend.model.Breakpoint;
import pe.edu.utec.atlasrambackend.model.BreakpointSite;
import pe.edu.utec.atlasrambackend.model.District;
import pe.edu.utec.atlasrambackend.model.Interpretation;
import pe.edu.utec.atlasrambackend.model.MicQualifier;
import pe.edu.utec.atlasrambackend.model.Ward;
import pe.edu.utec.atlasrambackend.repository.AntibiogramQueries;
import pe.edu.utec.atlasrambackend.repository.AntibioticRepository;
import pe.edu.utec.atlasrambackend.repository.BreakpointRepository;
import pe.edu.utec.atlasrambackend.repository.DistrictRepository;
import pe.edu.utec.atlasrambackend.repository.IsolateRepository;
import pe.edu.utec.atlasrambackend.repository.MicroorganismRepository;
import pe.edu.utec.atlasrambackend.stats.BetaBinomialPooling;
import pe.edu.utec.atlasrambackend.stats.CochranArmitage;
import pe.edu.utec.atlasrambackend.stats.Proportions;
import pe.edu.utec.atlasrambackend.model.DataOrigin;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AntibiogramService {

    static final int MIN_RESULTS = 30;
    /** Hospitales de referencia que se consideran: los que atienden a la mayoría de pacientes del distrito. */
    static final int MAX_REFERRAL_FACILITIES = 3;

    private final AntibiogramQueries antibiogramQueries;
    private final DistrictRepository districtRepository;
    private final MicroorganismRepository microorganismRepository;
    private final AntibioticRepository antibioticRepository;
    private final IsolateRepository isolateRepository;
    private final BreakpointRepository breakpointRepository;
    /** Edición activa de puntos de corte: la misma que usa InterpretationService al cargar. */
    private final String standard;
    private final String version;

    public AntibiogramService(AntibiogramQueries antibiogramQueries,
                              DistrictRepository districtRepository,
                              MicroorganismRepository microorganismRepository,
                              AntibioticRepository antibioticRepository,
                              IsolateRepository isolateRepository,
                              BreakpointRepository breakpointRepository,
                              @Value("${breakpoints.standard:CLSI}") String standard,
                              @Value("${breakpoints.version:2024}") String version) {
        this.breakpointRepository = breakpointRepository;
        this.standard = standard;
        this.version = version;
        this.antibiogramQueries = antibiogramQueries;
        this.districtRepository = districtRepository;
        this.microorganismRepository = microorganismRepository;
        this.antibioticRepository = antibioticRepository;
        this.isolateRepository = isolateRepository;
    }

    /**
     * Antibiograma acumulado según CLSI M39: primer aislamiento por paciente y especie, %S y %R con
     * intervalo de Wilson al 95 %. Por defecto solo devuelve combinaciones con al menos 30 aislamientos,
     * el mínimo de M39; con includeInsufficient también las demás, marcadas con sufficientData = false.
     */
    @Transactional(readOnly = true)
    public List<AntibiogramResponseDTO> calculate(Long microorganismId,
                                                  Long districtId,
                                                  LocalDate from,
                                                  LocalDate to,
                                                  boolean includeInsufficient,
                                                  DataOrigin origin) {
        return calculate(microorganismId, districtId, from, to, includeInsufficient, origin, null);
    }

    /** ward: antibiograma estratificado por servicio (CLSI M39), p. ej. solo UCI. null = todos. */
    @Transactional(readOnly = true)
    public List<AntibiogramResponseDTO> calculate(Long microorganismId,
                                                  Long districtId,
                                                  LocalDate from,
                                                  LocalDate to,
                                                  boolean includeInsufficient,
                                                  DataOrigin origin,
                                                  Ward ward) {
        validateRange(from, to);
        return toEntries(rows(microorganismId, districtId, null, from, to, origin, ward), includeInsufficient);
    }

    /** Filas de la consulta convertidas a la respuesta, con porcentajes e intervalos. */
    static List<AntibiogramResponseDTO> toEntries(List<AntibiogramQueries.Row> rows, boolean includeInsufficient) {
        return rows.stream()
                .map(AntibiogramService::toEntry)
                .filter(e -> includeInsufficient || e.sufficientData())
                .toList();
    }

    static AntibiogramResponseDTO toEntry(AntibiogramQueries.Row r) {
        Proportions.Interval s = Proportions.wilson(r.susceptible(), r.tested());
        Proportions.Interval res = Proportions.wilson(r.resistant(), r.tested());
        return new AntibiogramResponseDTO(
                r.microorganismName(), r.antibioticName(), r.tested(), r.resistant(),
                Proportions.percentage(r.resistant(), r.tested()),
                r.microorganismId(), r.antibioticId(), r.susceptible(), r.intermediate(),
                Proportions.percentage(r.susceptible(), r.tested()),
                s.low(), s.high(), res.low(), res.high(),
                r.tested() >= MIN_RESULTS,
                r.testedAll() - r.tested(),
                Proportions.percentage(r.identified(), r.tested()),
                Proportions.percentage(r.realCount(), r.tested()),
                r.site());
    }

    /**
     * Antibiograma con los pares por sitio de infección desdoblados. La consulta agregada mezcla las
     * interpretaciones de LCR (criterio meníngeo) con las del resto; para esos pares se reemplaza esa fila por
     * una por sitio, aplicando cada punto de corte a la CIM de todos los primeros aislamientos.
     */
    private List<AntibiogramQueries.Row> rows(Long microorganismId, Long districtId, java.util.Collection<Long> facilityIds,
                                              LocalDate from, LocalDate to, DataOrigin origin, Ward ward) {
        List<AntibiogramQueries.Row> rows = antibiogramQueries.antibiogram(microorganismId, districtId, facilityIds,
                from, to, origin, ward);
        List<Breakpoint> siteBreakpoints = breakpointRepository.findByStandardAndVersion(standard, version).stream()
                .filter(b -> b.getSite() != BreakpointSite.ANY)
                .toList();
        if (siteBreakpoints.isEmpty()) {
            return rows;
        }
        return splitBySite(rows, antibiogramQueries.siteResults(microorganismId, districtId, facilityIds, from, to,
                origin, ward, standard, version), siteBreakpoints);
    }

    /**
     * Por qué se aplica cada punto de corte a todos los aislamientos y no solo a los de su muestra: el
     * antibiograma responde "si el próximo paciente tiene meningitis, ¿qué probabilidad hay de que la
     * ceftriaxona sirva?". La distribución de CIM del neumococo es la misma; lo que cambia es el umbral. Así lo
     * reportan las redes de vigilancia (p. ej. SENTRY) y además el denominador no se reduce a los pocos LCR.
     * Un resultado cuya CIM o halo no se puede clasificar con ese punto de corte no entra en esa fila.
     */
    static List<AntibiogramQueries.Row> splitBySite(List<AntibiogramQueries.Row> rows,
                                                    List<AntibiogramQueries.SiteResultRow> results,
                                                    List<Breakpoint> siteBreakpoints) {
        Map<String, List<AntibiogramQueries.SiteResultRow>> byPair = results.stream()
                .collect(Collectors.groupingBy(r -> r.microorganismId() + ":" + r.antibioticId()));
        List<AntibiogramQueries.Row> out = new java.util.ArrayList<>();
        for (AntibiogramQueries.Row row : rows) {
            List<Breakpoint> bps = siteBreakpoints.stream()
                    .filter(b -> b.getMicroorganism().getId().equals(row.microorganismId())
                            && b.getAntibiotic().getId().equals(row.antibioticId()))
                    .sorted(java.util.Comparator.comparing(Breakpoint::getSite))
                    .toList();
            if (bps.isEmpty()) {
                out.add(row);
                continue;
            }
            List<AntibiogramQueries.SiteResultRow> pair = byPair.getOrDefault(
                    row.microorganismId() + ":" + row.antibioticId(), List.of());
            for (Breakpoint bp : bps) {
                long tested = 0, s = 0, i = 0, r = 0, identified = 0, real = 0, all = 0;
                for (AntibiogramQueries.SiteResultRow result : pair) {
                    Optional<Interpretation> category = classify(result, bp);
                    if (category.isEmpty()) {
                        continue;
                    }
                    all++;
                    if (!result.first()) {
                        continue;
                    }
                    tested++;
                    switch (category.get()) {
                        case SUSCEPTIBLE -> s++;
                        case INTERMEDIATE -> i++;
                        case RESISTANT -> r++;
                    }
                    if (result.identified()) identified++;
                    if (result.real()) real++;
                }
                if (tested > 0) {
                    out.add(new AntibiogramQueries.Row(row.microorganismId(), row.microorganismName(),
                            row.antibioticId(), row.antibioticName() + " (" + label(bp.getSite()) + ")",
                            tested, s, i, r, identified, all, real, bp.getSite()));
                }
            }
        }
        return out;
    }

    private static Optional<Interpretation> classify(AntibiogramQueries.SiteResultRow result, Breakpoint bp) {
        return classify(result.micValue(), result.micQualifier(), result.diskMm(), bp);
    }

    /** Mismo criterio que al cargar: primero la CIM y, si no es concluyente, el halo. */
    private static Optional<Interpretation> classify(java.math.BigDecimal mic, MicQualifier qualifier, Integer diskMm,
                                                     Breakpoint bp) {
        Optional<Interpretation> byMic = mic == null ? Optional.empty()
                : InterpretationService.classify(new ParsedMic(qualifier == null ? MicQualifier.EQ : qualifier, mic), bp);
        return byMic.or(() -> diskMm == null ? Optional.empty() : InterpretationService.classifyDisk(diskMm, bp));
    }

    /** Conteos de una clave (distrito o periodo) tras clasificar cada CIM con un punto de corte. */
    record Counts(long tested, long susceptible, long resistant) {
    }

    /**
     * Punto de corte del sitio pedido si el par tiene puntos de corte por sitio en la edición activa (por
     * defecto, no meningitis); vacío si el par no depende del sitio.
     */
    private Optional<Breakpoint> siteBreakpoint(Long microorganismId, Long antibioticId, BreakpointSite site) {
        List<Breakpoint> bySite = breakpointRepository.findAllByMicroorganismIdAndAntibioticIdAndStandardAndVersion(
                        microorganismId, antibioticId, standard, version).stream()
                .filter(b -> b.getSite() != BreakpointSite.ANY)
                .toList();
        if (bySite.isEmpty()) {
            return Optional.empty();
        }
        BreakpointSite wanted = site == null || site == BreakpointSite.ANY ? BreakpointSite.NON_MENINGITIS : site;
        return bySite.stream().filter(b -> b.getSite() == wanted).findFirst().or(() -> bySite.stream().findFirst());
    }

    /** Clasifica cada CIM (o halo) con el punto de corte; las que cruzan categorías no se cuentan. */
    static <K> Map<K, Counts> classifyByKey(List<AntibiogramQueries.KeyedResult<K>> results, Breakpoint bp) {
        Map<K, long[]> acc = new java.util.HashMap<>();
        for (AntibiogramQueries.KeyedResult<K> r : results) {
            Optional<Interpretation> category = classify(r.micValue(), r.micQualifier(), r.diskMm(), bp);
            if (category.isEmpty()) {
                continue;
            }
            long[] c = acc.computeIfAbsent(r.key(), k -> new long[3]);
            c[0]++;
            if (category.get() == Interpretation.SUSCEPTIBLE) c[1]++;
            if (category.get() == Interpretation.RESISTANT) c[2]++;
        }
        Map<K, Counts> out = new java.util.HashMap<>();
        acc.forEach((k, c) -> out.put(k, new Counts(c[0], c[1], c[2])));
        return out;
    }

    private static String label(BreakpointSite site) {
        return site == BreakpointSite.MENINGITIS ? "meningitis" : "no meningitis";
    }

    /**
     * Resistencia de un par microorganismo-antibiótico en cada distrito, para pintar el mapa (primer
     * aislamiento por paciente). Devuelve todos los distritos; los que no llegan a 30 vienen con
     * sufficientData en false y sin porcentaje, para mostrarlos en gris.
     */
    @Transactional(readOnly = true)
    public List<DistrictResistanceResponseDTO> calculateByDistrict(Long microorganismId,
                                                                   Long antibioticId,
                                                                   LocalDate from,
                                                                   LocalDate to,
                                                                   DataOrigin origin) {
        return calculateByDistrict(microorganismId, antibioticId, from, to, origin, null);
    }

    /**
     * site solo cuenta en los pares con punto de corte por sitio: la CIM de cada primer aislamiento se
     * clasifica con el de ese sitio (por defecto, no meningitis) en vez de mezclar las dos lecturas.
     */
    @Transactional(readOnly = true)
    public List<DistrictResistanceResponseDTO> calculateByDistrict(Long microorganismId,
                                                                   Long antibioticId,
                                                                   LocalDate from,
                                                                   LocalDate to,
                                                                   DataOrigin origin,
                                                                   BreakpointSite site) {
        validateRange(from, to);
        if (!microorganismRepository.existsById(microorganismId)) {
            throw new ResourceNotFoundException("Microorganismo", microorganismId);
        }
        if (!antibioticRepository.existsById(antibioticId)) {
            throw new ResourceNotFoundException("Antibiótico", antibioticId);
        }
        Optional<Breakpoint> siteBreakpoint = siteBreakpoint(microorganismId, antibioticId, site);
        Map<Long, AntibiogramQueries.DistrictRow> rows = siteBreakpoint
                .map(bp -> classifyByKey(antibiogramQueries.districtResults(microorganismId, antibioticId, from, to,
                        origin), bp).entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> new AntibiogramQueries.DistrictRow(
                                e.getKey(), e.getValue().tested(), e.getValue().susceptible(), e.getValue().resistant()))))
                .orElseGet(() -> antibiogramQueries.byDistrict(microorganismId, antibioticId, from, to, origin).stream()
                        .collect(Collectors.toMap(AntibiogramQueries.DistrictRow::districtId, Function.identity())));
        BreakpointSite used = siteBreakpoint.map(Breakpoint::getSite).orElse(null);
        BetaBinomialPooling.Prior prior = BetaBinomialPooling.fit(rows.values().stream()
                .map(r -> new BetaBinomialPooling.Group(r.resistant(), r.tested()))
                .toList());
        return districtRepository.findAll(Sort.by("ubigeo")).stream()
                .map(district -> toResponse(district, rows.get(district.getId()), used, prior))
                .toList();
    }

    /**
     * Antibiograma de los hospitales de referencia de un distrito. Un distrito pequeño (por ejemplo,
     * sin hospital propio) rara vez reúne 30 resultados por combinación; lo útil para su médico es
     * la resistencia en los hospitales donde se atienden sus pacientes. Se toman los que más pacientes
     * del distrito reciben y se calcula el antibiograma con todos los aislamientos de esos hospitales.
     */
    @Transactional(readOnly = true)
    public ReferralAntibiogramResponseDTO calculateReferral(Long districtId, LocalDate from, LocalDate to,
                                                            DataOrigin origin) {
        validateRange(from, to);
        District district = districtRepository.findById(districtId)
                .orElseThrow(() -> new ResourceNotFoundException("Distrito", districtId));

        List<ReferralFacilityRow> rows = isolateRepository.countByFacilityForDistrict(districtId, from, to);
        long districtTotal = rows.stream().mapToLong(ReferralFacilityRow::isolateCount).sum();
        List<ReferralFacilityRow> top = rows.stream().limit(MAX_REFERRAL_FACILITIES).toList();

        List<ReferralFacilityDTO> facilities = top.stream()
                .map(row -> new ReferralFacilityDTO(row.facilityId(), row.facilityName(), row.isolateCount(),
                        row.isolateCount() * 100.0 / districtTotal))
                .toList();
        List<AntibiogramResponseDTO> entries = top.isEmpty()
                ? List.of()
                : toEntries(rows(null, null,
                        top.stream().map(ReferralFacilityRow::facilityId).toList(), from, to, origin, null), false);

        return new ReferralAntibiogramResponseDTO(district.getName(), facilities, entries);
    }

    /**
     * Antibiograma acumulado de un establecimiento (para el reporte PROA): todas las combinaciones, también
     * las que no llegan a 30, marcadas con sufficientData = false.
     */
    @Transactional(readOnly = true)
    public List<AntibiogramResponseDTO> calculateForFacility(Long facilityId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        return toEntries(rows(null, null, List.of(facilityId), from, to, null, null), true);
    }

    /** Agrupación temporal de la tendencia. */
    public enum TrendInterval {
        MONTH("month", 12), QUARTER("quarter", 4);

        final String sqlUnit;
        final int perYear;

        TrendInterval(String sqlUnit, int perYear) {
            this.sqlUnit = sqlUnit;
            this.perYear = perYear;
        }

        LocalDate start(LocalDate date) {
            LocalDate month = date.withDayOfMonth(1);
            return this == MONTH ? month : month.withMonth(((date.getMonthValue() - 1) / 3) * 3 + 1);
        }

        LocalDate next(LocalDate start) {
            return this == MONTH ? start.plusMonths(1) : start.plusMonths(3);
        }
    }

    static final int MAX_TREND_YEARS = 10;

    /**
     * Resistencia de un par microorganismo-antibiótico periodo a periodo, con la prueba de tendencia de
     * Cochran-Armitage. Los periodos sin aislamientos aparecen con 0 probados para que el eje del tiempo
     * no tenga huecos. facilityId es opcional (un hospital); districtId también.
     */
    @Transactional(readOnly = true)
    public TrendResponseDTO trend(Long microorganismId, Long antibioticId, Long districtId, Long facilityId,
                                  LocalDate from, LocalDate to, TrendInterval interval, DataOrigin origin) {
        return trend(microorganismId, antibioticId, districtId, facilityId, from, to, interval, origin, null);
    }

    /** site: ver calculateByDistrict. */
    @Transactional(readOnly = true)
    public TrendResponseDTO trend(Long microorganismId, Long antibioticId, Long districtId, Long facilityId,
                                  LocalDate from, LocalDate to, TrendInterval interval, DataOrigin origin,
                                  BreakpointSite site) {
        validateRange(from, to);
        if (from.plusYears(MAX_TREND_YEARS).isBefore(to)) {
            throw new BusinessRuleException("El periodo de la tendencia no puede superar " + MAX_TREND_YEARS + " años");
        }
        var microorganism = microorganismRepository.findById(microorganismId)
                .orElseThrow(() -> new ResourceNotFoundException("Microorganismo", microorganismId));
        var antibiotic = antibioticRepository.findById(antibioticId)
                .orElseThrow(() -> new ResourceNotFoundException("Antibiótico", antibioticId));

        List<Long> facilities = facilityId == null ? null : List.of(facilityId);
        Optional<Breakpoint> siteBreakpoint = siteBreakpoint(microorganismId, antibioticId, site);
        Map<LocalDate, AntibiogramQueries.PeriodRow> rows = siteBreakpoint
                .map(bp -> classifyByKey(antibiogramQueries.trendResults(microorganismId, antibioticId, districtId,
                        facilities, from, to, interval.sqlUnit, origin), bp).entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> new AntibiogramQueries.PeriodRow(
                                e.getKey(), e.getValue().tested(), e.getValue().susceptible(), e.getValue().resistant()))))
                .orElseGet(() -> antibiogramQueries.trend(microorganismId, antibioticId, districtId, facilities,
                                from, to, interval.sqlUnit, origin).stream()
                        .collect(Collectors.toMap(AntibiogramQueries.PeriodRow::period, Function.identity())));

        List<TrendResponseDTO.Point> points = new java.util.ArrayList<>();
        List<CochranArmitage.Point> testPoints = new java.util.ArrayList<>();
        int index = 0;
        for (LocalDate start = interval.start(from); !start.isAfter(to); start = interval.next(start), index++) {
            AntibiogramQueries.PeriodRow row = rows.get(start);
            long tested = row == null ? 0 : row.tested();
            long resistant = row == null ? 0 : row.resistant();
            long susceptible = row == null ? 0 : row.susceptible();
            Proportions.Interval ci = Proportions.wilson(resistant, tested);
            points.add(new TrendResponseDTO.Point(start, tested, resistant,
                    tested == 0 ? null : Proportions.percentage(resistant, tested),
                    tested == 0 ? null : ci.low(),
                    tested == 0 ? null : ci.high(),
                    tested == 0 ? null : Proportions.percentage(susceptible, tested)));
            testPoints.add(new CochranArmitage.Point(index, resistant, tested));
        }

        String species = microorganism.getSpecies();
        return new TrendResponseDTO(microorganismId,
                species == null ? microorganism.getGenus() : microorganism.getGenus() + " " + species,
                antibioticId, antibiotic.getName(), interval.name(), points,
                toTrend(CochranArmitage.test(testPoints), interval),
                siteBreakpoint.map(Breakpoint::getSite).orElse(null));
    }

    static TrendResponseDTO.Trend toTrend(CochranArmitage.Result r, TrendInterval interval) {
        if (r == null) {
            return null;
        }
        String direction = r.pValue() >= 0.05 ? "NO_SIGNIFICANT_TREND" : r.z() > 0 ? "INCREASING" : "DECREASING";
        return new TrendResponseDTO.Trend(r.z(), r.pValue(), r.slope() * 100 * interval.perYear, direction,
                r.periods(), r.totalTrials());
    }

    private DistrictResistanceResponseDTO toResponse(District district, AntibiogramQueries.DistrictRow row,
                                                     BreakpointSite site, BetaBinomialPooling.Prior prior) {
        long total = row == null ? 0 : row.tested();
        long resistant = row == null ? 0 : row.resistant();
        boolean sufficient = total >= MIN_RESULTS;
        Proportions.Interval ci = Proportions.wilson(resistant, total);
        BetaBinomialPooling.Estimate pooled = BetaBinomialPooling.estimate(prior, resistant, total);
        return new DistrictResistanceResponseDTO(district.getId(), district.getUbigeo(), district.getName(),
                total, resistant,
                sufficient ? Proportions.percentage(resistant, total) : null,
                sufficient,
                sufficient ? ci.low() : null,
                sufficient ? ci.high() : null,
                site,
                pooled == null ? null : pooled.percentage(),
                pooled == null ? null : pooled.low(),
                pooled == null ? null : pooled.high());
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessRuleException("La fecha inicial no puede ser posterior a la final");
        }
    }
}
