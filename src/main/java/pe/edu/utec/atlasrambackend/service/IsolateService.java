package pe.edu.utec.atlasrambackend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.dto.CreateIsolateDTO;
import pe.edu.utec.atlasrambackend.dto.CreateIsolateResultDTO;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.interpretation.MicParser;
import pe.edu.utec.atlasrambackend.interpretation.ParsedMic;
import pe.edu.utec.atlasrambackend.model.Antibiotic;
import pe.edu.utec.atlasrambackend.model.SusceptibilityResult;
import pe.edu.utec.atlasrambackend.repository.AntibioticRepository;
import pe.edu.utec.atlasrambackend.dto.IsolateResponseDTO;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.mapper.IsolateMapper;
import pe.edu.utec.atlasrambackend.model.Isolate;
import pe.edu.utec.atlasrambackend.model.Specimen;
import pe.edu.utec.atlasrambackend.repository.DistrictRepository;
import pe.edu.utec.atlasrambackend.repository.FacilityRepository;
import pe.edu.utec.atlasrambackend.repository.IsolateRepository;
import pe.edu.utec.atlasrambackend.repository.IsolateSpecifications;
import pe.edu.utec.atlasrambackend.repository.MicroorganismRepository;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import pe.edu.utec.atlasrambackend.model.DataOrigin;

@Service
public class IsolateService {

    private final IsolateRepository isolateRepository;
    private final FacilityRepository facilityRepository;
    private final DistrictRepository districtRepository;
    private final MicroorganismRepository microorganismRepository;
    private final IsolateMapper isolateMapper;
    private final AntibioticRepository antibioticRepository;
    private final InterpretationService interpretationService;

    public IsolateService(IsolateRepository isolateRepository,
                          FacilityRepository facilityRepository,
                          DistrictRepository districtRepository,
                          MicroorganismRepository microorganismRepository,
                          IsolateMapper isolateMapper,
                          AntibioticRepository antibioticRepository,
                          InterpretationService interpretationService) {
        this.antibioticRepository = antibioticRepository;
        this.interpretationService = interpretationService;
        this.isolateRepository = isolateRepository;
        this.facilityRepository = facilityRepository;
        this.districtRepository = districtRepository;
        this.microorganismRepository = microorganismRepository;
        this.isolateMapper = isolateMapper;
    }

    @Transactional(readOnly = true)
    public Page<IsolateResponseDTO> findAll(Pageable pageable) {
        return isolateRepository.findAll(pageable).map(isolateMapper::toResponse);
    }

    /** Listado con filtros opcionales por establecimiento, microorganismo y tipo de muestra. */
    @Transactional(readOnly = true)
    public Page<IsolateResponseDTO> search(Long facilityId, Long microorganismId, Specimen specimen,
                                           Collection<Long> allowedFacilityIds, Pageable pageable) {
        return isolateRepository
                .findAll(IsolateSpecifications.withFilters(facilityId, microorganismId, specimen, allowedFacilityIds),
                        pageable)
                .map(isolateMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public IsolateResponseDTO findById(Long id) {
        Isolate isolate = isolateRepository.findByIdWithRelations(id);
        if (isolate == null) {
            throw new ResourceNotFoundException("Aislamiento", id);
        }
        return isolateMapper.toResponse(isolate);
    }

    @Transactional
    public IsolateResponseDTO create(CreateIsolateDTO dto) {
        Isolate isolate = new Isolate();
        isolate.setCollectionDate(dto.collectionDate());
        isolate.setSpecimenType(dto.specimenType());
        isolate.setPatientAge(dto.patientAge());
        isolate.setPatientSex(dto.patientSex());
        isolate.setDataOrigin(dto.dataOrigin() == null ? DataOrigin.SYNTHETIC : dto.dataOrigin());
        isolate.setWard(dto.ward());
        isolate.setFacility(facilityRepository.findById(dto.facilityId())
                .orElseThrow(() -> new ResourceNotFoundException("Establecimiento", dto.facilityId())));
        isolate.setMicroorganism(microorganismRepository.findById(dto.microorganismId())
                .orElseThrow(() -> new ResourceNotFoundException("Microorganismo", dto.microorganismId())));
        if (dto.districtId() != null) {
            isolate.setDistrict(districtRepository.findById(dto.districtId())
                    .orElseThrow(() -> new ResourceNotFoundException("Distrito", dto.districtId())));
        }
        Set<Long> seen = new HashSet<>();
        for (CreateIsolateResultDTO entry : dto.results()) {
            if (!seen.add(entry.antibioticId())) {
                throw new BusinessRuleException("Un antibiótico aparece dos veces en el antibiograma");
            }
            isolate.addResult(toResult(isolate, entry));
        }
        // Una sola transacción: si algún resultado falla, no se guarda nada.
        return isolateMapper.toResponse(isolateRepository.save(isolate));
    }

    /** Mismo criterio que la carga CSV: CIM, si no el halo, y si ninguno es concluyente, lo que informó el laboratorio. */
    private SusceptibilityResult toResult(Isolate isolate, CreateIsolateResultDTO entry) {
        Antibiotic antibiotic = antibioticRepository.findById(entry.antibioticId())
                .orElseThrow(() -> new ResourceNotFoundException("Antibiótico", entry.antibioticId()));
        ParsedMic mic;
        try {
            mic = MicParser.parse(entry.mic()).orElse(null);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(antibiotic.getName() + ": " + e.getMessage());
        }
        if (mic == null && entry.diskMm() == null && entry.reportedInterpretation() == null) {
            throw new BusinessRuleException(antibiotic.getName() + ": indique la CIM, el halo o la interpretación");
        }
        InterpretationService.Resolution resolution = interpretationService
                .resolve(mic, entry.diskMm(), entry.reportedInterpretation(), isolate.getMicroorganism(), antibiotic,
                        isolate.getSpecimenType())
                .orElseThrow(() -> new BusinessRuleException(antibiotic.getName()
                        + ": no hay punto de corte para interpretar ese valor; indique también la interpretación"
                        + " del laboratorio (S, I o R)"));
        SusceptibilityResult result = new SusceptibilityResult();
        result.setAntibiotic(antibiotic);
        result.setMicValue(mic == null ? null : mic.value());
        result.setMicQualifier(mic == null ? null : mic.qualifier());
        result.setDiskDiffusionMm(entry.diskMm());
        result.setReportedInterpretation(entry.reportedInterpretation());
        result.setInterpretation(resolution.interpretation());
        result.setInterpretationSource(resolution.source());
        result.setBreakpointStandard(resolution.breakpointStandard());
        result.setBreakpointVersion(resolution.breakpointVersion());
        return result;
    }

    @Transactional
    public void delete(Long id) {
        Isolate isolate = isolateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aislamiento", id));
        isolateRepository.delete(isolate);
    }
}
