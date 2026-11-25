package pe.edu.utec.atlasrambackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.dto.BreakpointEditionDTO;
import pe.edu.utec.atlasrambackend.dto.ReinterpretationReportDTO;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.interpretation.ParsedMic;
import pe.edu.utec.atlasrambackend.model.Breakpoint;
import pe.edu.utec.atlasrambackend.model.Interpretation;
import pe.edu.utec.atlasrambackend.model.InterpretationSource;
import pe.edu.utec.atlasrambackend.model.MicQualifier;
import pe.edu.utec.atlasrambackend.repository.BreakpointRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reinterpreta las CIM y los halos ya guardados con una edición de puntos de corte.
 *
 * CLSI publica una edición nueva de M100 cada año y a veces cambia puntos de corte (en 2023, los aminoglucósidos
 * en Enterobacterales). El laboratorio guarda el valor medido, no solo la categoría, así que el histórico se
 * puede volver a interpretar sin repetir ninguna prueba; es lo mismo que hace WHONET.
 *
 * - Vista previa con cualquier edición cargada: dice cuántos resultados cambiarían, sin tocar nada.
 * - Aplicar solo con la edición activa (variable BREAKPOINTS_VERSION), para que el histórico y las cargas
 *   nuevas usen la misma tabla.
 * Los valores de cada edición los carga el laboratorio desde su copia de CLSI M100: el sistema no distribuye
 * las tablas de CLSI.
 */
@Service
public class ReinterpretationService {

    static final int MAX_CHANGE_GROUPS = 50;
    private static final int BATCH = 1000;

    private final BreakpointRepository breakpointRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final String activeStandard;
    private final String activeVersion;

    public ReinterpretationService(BreakpointRepository breakpointRepository, NamedParameterJdbcTemplate jdbc,
                                   @Value("${breakpoints.standard:CLSI}") String activeStandard,
                                   @Value("${breakpoints.version:2024}") String activeVersion) {
        this.breakpointRepository = breakpointRepository;
        this.jdbc = jdbc;
        this.activeStandard = activeStandard;
        this.activeVersion = activeVersion;
    }

    /** Un resultado guardado, con lo necesario para reinterpretarlo. */
    record Row(long id, long microorganismId, long antibioticId, String microorganism, String antibiotic,
               BigDecimal mic, MicQualifier qualifier, Integer diskMm, Interpretation current,
               Interpretation reported, InterpretationSource source, String standard, String version) {
    }

    /** Cómo queda un resultado con la edición nueva. */
    record Outcome(Interpretation interpretation, InterpretationSource source, String standard, String version) {
    }

    public List<BreakpointEditionDTO> editions() {
        return breakpointRepository.countByEdition().stream()
                .map(r -> new BreakpointEditionDTO((String) r[0], (String) r[1], ((Number) r[2]).longValue(),
                        activeStandard.equals(r[0]) && activeVersion.equals(r[1])))
                .toList();
    }

    @Transactional
    public ReinterpretationReportDTO reinterpret(String version, boolean apply) {
        String standard = activeStandard;
        if (apply && !activeVersion.equals(version)) {
            throw new BusinessRuleException("Solo se puede aplicar la edición activa (" + standard + " " + activeVersion
                    + "). Para pasar a " + version + ", primero cambie BREAKPOINTS_VERSION en el servidor.");
        }
        Map<String, Breakpoint> breakpoints = new HashMap<>();
        breakpointRepository.findByStandardAndVersion(standard, version).forEach(bp ->
                breakpoints.put(key(bp.getMicroorganism().getId(), bp.getAntibiotic().getId()), bp));
        if (breakpoints.isEmpty()) {
            throw new BusinessRuleException("No hay puntos de corte cargados para " + standard + " " + version);
        }

        List<Row> rows = jdbc.query("""
                select r.id, i.microorganism_id, r.antibiotic_id,
                       m.genus || ' ' || coalesce(m.species, '') as microorganism, a.name as antibiotic,
                       r.mic_value, r.mic_qualifier, r.disk_diffusion_mm, r.interpretation,
                       r.reported_interpretation, r.interpretation_source, r.breakpoint_standard, r.breakpoint_version
                from susceptibility_result r
                join isolate i on i.id = r.isolate_id
                join microorganism m on m.id = i.microorganism_id
                join antibiotic a on a.id = r.antibiotic_id
                where r.mic_value is not null or r.disk_diffusion_mm is not null
                """, new MapSqlParameterSource(), (rs, n) -> new Row(
                rs.getLong("id"), rs.getLong("microorganism_id"), rs.getLong("antibiotic_id"),
                rs.getString("microorganism").trim(), rs.getString("antibiotic"),
                rs.getBigDecimal("mic_value"),
                rs.getString("mic_qualifier") == null ? null : MicQualifier.valueOf(rs.getString("mic_qualifier")),
                (Integer) rs.getObject("disk_diffusion_mm"),
                Interpretation.valueOf(rs.getString("interpretation")),
                rs.getString("reported_interpretation") == null ? null
                        : Interpretation.valueOf(rs.getString("reported_interpretation")),
                InterpretationSource.valueOf(rs.getString("interpretation_source")),
                rs.getString("breakpoint_standard"), rs.getString("breakpoint_version")));

        int withoutBreakpoint = 0;
        int changed = 0;
        Map<String, int[]> groups = new HashMap<>();
        List<MapSqlParameterSource> updates = new ArrayList<>();
        for (Row row : rows) {
            Breakpoint bp = breakpoints.get(key(row.microorganismId(), row.antibioticId()));
            if (bp == null) {
                withoutBreakpoint++;
                continue;
            }
            Optional<Outcome> outcome = reinterpretRow(row, bp, standard, version);
            if (outcome.isEmpty()) {
                continue;
            }
            Outcome o = outcome.get();
            if (o.interpretation() != row.current()) {
                changed++;
                groups.computeIfAbsent(String.join("|", row.microorganism(), row.antibiotic(),
                        row.current().name(), o.interpretation().name()), k -> new int[1])[0]++;
            }
            boolean differs = o.interpretation() != row.current() || o.source() != row.source()
                    || !Objects.equals(o.standard(), row.standard()) || !Objects.equals(o.version(), row.version());
            if (apply && differs) {
                updates.add(new MapSqlParameterSource()
                        .addValue("id", row.id())
                        .addValue("interpretation", o.interpretation().name())
                        .addValue("source", o.source().name())
                        .addValue("standard", o.standard())
                        .addValue("version", o.version()));
            }
        }
        for (int from = 0; from < updates.size(); from += BATCH) {
            jdbc.batchUpdate("""
                    update susceptibility_result
                    set interpretation = :interpretation, interpretation_source = :source,
                        breakpoint_standard = :standard, breakpoint_version = :version
                    where id = :id
                    """, updates.subList(from, Math.min(from + BATCH, updates.size()))
                    .toArray(MapSqlParameterSource[]::new));
        }
        List<ReinterpretationReportDTO.Change> changes = groups.entrySet().stream()
                .map(e -> {
                    String[] p = e.getKey().split("\\|");
                    return new ReinterpretationReportDTO.Change(p[0], p[1], p[2], p[3], e.getValue()[0]);
                })
                .sorted(Comparator.comparingInt(ReinterpretationReportDTO.Change::count).reversed())
                .limit(MAX_CHANGE_GROUPS)
                .toList();
        return new ReinterpretationReportDTO(standard, version, apply, rows.size(), changed, withoutBreakpoint,
                changes);
    }

    /**
     * Misma regla que al cargar: CIM concluyente → halo → interpretación del laboratorio. Vacío si no queda
     * ninguna (no se inventa una categoría).
     */
    static Optional<Outcome> reinterpretRow(Row row, Breakpoint bp, String standard, String version) {
        Optional<Interpretation> calculated = Optional.ofNullable(row.mic())
                .flatMap(mic -> InterpretationService.classify(
                        new ParsedMic(row.qualifier() == null ? MicQualifier.EQ : row.qualifier(), mic), bp))
                .or(() -> Optional.ofNullable(row.diskMm()).flatMap(d -> InterpretationService.classifyDisk(d, bp)));
        if (calculated.isPresent()) {
            return Optional.of(new Outcome(calculated.get(), InterpretationSource.CALCULATED, standard, version));
        }
        return Optional.ofNullable(row.reported())
                .map(r -> new Outcome(r, InterpretationSource.LAB_REPORTED, null, null));
    }

    private static String key(Long microorganismId, Long antibioticId) {
        return microorganismId + "|" + antibioticId;
    }
}
