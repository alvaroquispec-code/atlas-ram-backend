package pe.edu.utec.atlasrambackend.service;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.dto.AntibiogramResponseDTO;
import pe.edu.utec.atlasrambackend.dto.DistrictResistanceResponseDTO;
import pe.edu.utec.atlasrambackend.dto.DistrictResistanceRow;
import pe.edu.utec.atlasrambackend.dto.ReferralAntibiogramResponseDTO;
import pe.edu.utec.atlasrambackend.dto.ReferralFacilityDTO;
import pe.edu.utec.atlasrambackend.dto.ReferralFacilityRow;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.model.District;
import pe.edu.utec.atlasrambackend.repository.AntibioticRepository;
import pe.edu.utec.atlasrambackend.repository.DistrictRepository;
import pe.edu.utec.atlasrambackend.repository.IsolateRepository;
import pe.edu.utec.atlasrambackend.repository.MicroorganismRepository;
import pe.edu.utec.atlasrambackend.repository.SusceptibilityResultRepository;

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

    private final SusceptibilityResultRepository resultRepository;
    private final DistrictRepository districtRepository;
    private final MicroorganismRepository microorganismRepository;
    private final AntibioticRepository antibioticRepository;
    private final IsolateRepository isolateRepository;

    public AntibiogramService(SusceptibilityResultRepository resultRepository,
                              DistrictRepository districtRepository,
                              MicroorganismRepository microorganismRepository,
                              AntibioticRepository antibioticRepository,
                              IsolateRepository isolateRepository) {
        this.resultRepository = resultRepository;
        this.districtRepository = districtRepository;
        this.microorganismRepository = microorganismRepository;
        this.antibioticRepository = antibioticRepository;
        this.isolateRepository = isolateRepository;
    }

    /**
     * Antibiograma acumulado. Solo devuelve combinaciones con al menos 30 resultados,
     * como recomienda CLSI M39; por debajo de ese umbral el porcentaje no es interpretable.
     */
    @Transactional(readOnly = true)
    public List<AntibiogramResponseDTO> calculate(Long microorganismId,
                                                  Long districtId,
                                                  LocalDate from,
                                                  LocalDate to) {
        validateRange(from, to);
        return resultRepository.calculateAntibiogram(microorganismId, districtId, from, to);
    }

    /**
     * Resistencia de un par microorganismo-antibiótico en cada distrito, para pintar el mapa.
     * Devuelve todos los distritos; los que no llegan a 30 resultados vienen con
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
        Map<Long, DistrictResistanceRow> rows = resultRepository
                .calculateResistanceByDistrict(microorganismId, antibioticId, from, to).stream()
                .collect(Collectors.toMap(DistrictResistanceRow::districtId, Function.identity()));
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
                : resultRepository.calculateAntibiogramForFacilities(
                        top.stream().map(ReferralFacilityRow::facilityId).toList(), from, to);

        return new ReferralAntibiogramResponseDTO(district.getName(), facilities, entries);
    }

    private DistrictResistanceResponseDTO toResponse(District district, DistrictResistanceRow row) {
        long total = row == null ? 0 : row.totalTested();
        long resistant = row == null ? 0 : row.resistantCount();
        boolean sufficient = total >= MIN_RESULTS;
        Double percentage = sufficient ? resistant * 100.0 / total : null;
        return new DistrictResistanceResponseDTO(district.getId(), district.getUbigeo(),
                district.getName(), total, resistant, percentage, sufficient);
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessRuleException("La fecha inicial no puede ser posterior a la final");
        }
    }
}
