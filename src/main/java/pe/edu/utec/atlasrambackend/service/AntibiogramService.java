package pe.edu.utec.atlasrambackend.service;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.dto.AntibiogramResponseDTO;
import pe.edu.utec.atlasrambackend.dto.DistrictResistanceResponseDTO;
import pe.edu.utec.atlasrambackend.dto.ReferralAntibiogramResponseDTO;
import pe.edu.utec.atlasrambackend.dto.ReferralFacilityDTO;
import pe.edu.utec.atlasrambackend.dto.ReferralFacilityRow;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.model.District;
import pe.edu.utec.atlasrambackend.repository.AntibiogramQueries;
import pe.edu.utec.atlasrambackend.repository.AntibioticRepository;
import pe.edu.utec.atlasrambackend.repository.DistrictRepository;
import pe.edu.utec.atlasrambackend.repository.IsolateRepository;
import pe.edu.utec.atlasrambackend.repository.MicroorganismRepository;
import pe.edu.utec.atlasrambackend.stats.Proportions;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

    public AntibiogramService(AntibiogramQueries antibiogramQueries,
                              DistrictRepository districtRepository,
                              MicroorganismRepository microorganismRepository,
                              AntibioticRepository antibioticRepository,
                              IsolateRepository isolateRepository) {
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
                                                  boolean includeInsufficient) {
        validateRange(from, to);
        return toEntries(antibiogramQueries.antibiogram(microorganismId, districtId, null, from, to),
                includeInsufficient);
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
                Proportions.percentage(r.identified(), r.tested()));
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
                                                                   LocalDate to) {
        validateRange(from, to);
        if (!microorganismRepository.existsById(microorganismId)) {
            throw new ResourceNotFoundException("Microorganismo", microorganismId);
        }
        if (!antibioticRepository.existsById(antibioticId)) {
            throw new ResourceNotFoundException("Antibiótico", antibioticId);
        }
        Map<Long, AntibiogramQueries.DistrictRow> rows = antibiogramQueries
                .byDistrict(microorganismId, antibioticId, from, to).stream()
                .collect(Collectors.toMap(AntibiogramQueries.DistrictRow::districtId, Function.identity()));
        return districtRepository.findAll(Sort.by("ubigeo")).stream()
                .map(district -> toResponse(district, rows.get(district.getId())))
                .toList();
    }

    /**
     * Antibiograma de los hospitales de referencia de un distrito. Un distrito pequeño (por ejemplo,
     * sin hospital propio) rara vez reúne 30 resultados por combinación; lo útil para su médico es
     * la resistencia en los hospitales donde se atienden sus pacientes. Se toman los que más pacientes
     * del distrito reciben y se calcula el antibiograma con todos los aislamientos de esos hospitales.
     */
    @Transactional(readOnly = true)
    public ReferralAntibiogramResponseDTO calculateReferral(Long districtId, LocalDate from, LocalDate to) {
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
                : toEntries(antibiogramQueries.antibiogram(null, null,
                        top.stream().map(ReferralFacilityRow::facilityId).toList(), from, to), false);

        return new ReferralAntibiogramResponseDTO(district.getName(), facilities, entries);
    }

    private DistrictResistanceResponseDTO toResponse(District district, AntibiogramQueries.DistrictRow row) {
        long total = row == null ? 0 : row.tested();
        long resistant = row == null ? 0 : row.resistant();
        boolean sufficient = total >= MIN_RESULTS;
        Proportions.Interval ci = Proportions.wilson(resistant, total);
        return new DistrictResistanceResponseDTO(district.getId(), district.getUbigeo(), district.getName(),
                total, resistant,
                sufficient ? Proportions.percentage(resistant, total) : null,
                sufficient,
                sufficient ? ci.low() : null,
                sufficient ? ci.high() : null);
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessRuleException("La fecha inicial no puede ser posterior a la final");
        }
    }
}
