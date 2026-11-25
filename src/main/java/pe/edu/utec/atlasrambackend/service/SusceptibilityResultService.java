package pe.edu.utec.atlasrambackend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.dto.CreateSusceptibilityResultDTO;
import pe.edu.utec.atlasrambackend.dto.SusceptibilityResultResponseDTO;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.exception.DuplicateResourceException;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.interpretation.ParsedMic;
import pe.edu.utec.atlasrambackend.mapper.SusceptibilityResultMapper;
import pe.edu.utec.atlasrambackend.model.Antibiotic;
import pe.edu.utec.atlasrambackend.model.Isolate;
import pe.edu.utec.atlasrambackend.model.MicQualifier;
import pe.edu.utec.atlasrambackend.model.SusceptibilityResult;
import pe.edu.utec.atlasrambackend.repository.AntibioticRepository;
import pe.edu.utec.atlasrambackend.repository.IsolateRepository;
import pe.edu.utec.atlasrambackend.repository.SusceptibilityResultRepository;

import java.util.List;

@Service
public class SusceptibilityResultService {

    private final SusceptibilityResultRepository resultRepository;
    private final IsolateRepository isolateRepository;
    private final AntibioticRepository antibioticRepository;
    private final InterpretationService interpretationService;
    private final SusceptibilityResultMapper resultMapper;

    public SusceptibilityResultService(SusceptibilityResultRepository resultRepository,
                                       IsolateRepository isolateRepository,
                                       AntibioticRepository antibioticRepository,
                                       InterpretationService interpretationService,
                                       SusceptibilityResultMapper resultMapper) {
        this.resultRepository = resultRepository;
        this.isolateRepository = isolateRepository;
        this.antibioticRepository = antibioticRepository;
        this.interpretationService = interpretationService;
        this.resultMapper = resultMapper;
    }

    @Transactional(readOnly = true)
    public List<SusceptibilityResultResponseDTO> findByIsolate(Long isolateId) {
        if (!isolateRepository.existsById(isolateId)) {
            throw new ResourceNotFoundException("Aislamiento", isolateId);
        }
        return resultRepository.findByIsolateIdOrderByAntibioticNameAsc(isolateId).stream()
                .map(resultMapper::toResponse)
                .toList();
    }

    /**
     * La interpretación la calcula el sistema contra el punto de corte vigente. Si no hay punto de corte
     * o la CIM no es concluyente, se guarda la del laboratorio marcada como LAB_REPORTED (sin sello de
     * estándar). Si no hay ninguna de las dos, el resultado se rechaza.
     */
    @Transactional
    public SusceptibilityResultResponseDTO create(CreateSusceptibilityResultDTO dto) {
        if (dto.micValue() == null && dto.diskDiffusionMm() == null) {
            throw new BusinessRuleException("El resultado necesita una CIM o un diámetro de halo");
        }
        if (dto.micQualifier() != null && dto.micValue() == null) {
            throw new BusinessRuleException("El calificador de la CIM requiere un valor de CIM");
        }
        if (resultRepository.existsByIsolateIdAndAntibioticId(dto.isolateId(), dto.antibioticId())) {
            throw new DuplicateResourceException(
                    "Ya existe un resultado para ese aislamiento y antibiótico");
        }
        Isolate isolate = isolateRepository.findById(dto.isolateId())
                .orElseThrow(() -> new ResourceNotFoundException("Aislamiento", dto.isolateId()));
        Antibiotic antibiotic = antibioticRepository.findById(dto.antibioticId())
                .orElseThrow(() -> new ResourceNotFoundException("Antibiótico", dto.antibioticId()));

        ParsedMic mic = dto.micValue() == null ? null
                : new ParsedMic(dto.micQualifier() == null ? MicQualifier.EQ : dto.micQualifier(), dto.micValue());

        InterpretationService.Resolution resolution = interpretationService
                .resolve(mic, dto.diskDiffusionMm(), dto.reportedInterpretation(), isolate.getMicroorganism(), antibiotic,
                        isolate.getSpecimenType())
                .orElseThrow(() -> new BusinessRuleException(
                        "No hay punto de corte (de CIM o de disco) para esa combinación, o el valor no es concluyente, "
                                + "y el laboratorio no reportó interpretación"));

        SusceptibilityResult result = new SusceptibilityResult();
        result.setIsolate(isolate);
        result.setAntibiotic(antibiotic);
        result.setMicValue(dto.micValue());
        result.setMicQualifier(mic == null ? null : mic.qualifier());
        result.setDiskDiffusionMm(dto.diskDiffusionMm());
        result.setReportedInterpretation(dto.reportedInterpretation());
        result.setInterpretation(resolution.interpretation());
        result.setInterpretationSource(resolution.source());
        result.setBreakpointStandard(resolution.breakpointStandard());
        result.setBreakpointVersion(resolution.breakpointVersion());

        return resultMapper.toResponse(resultRepository.save(result));
    }

    @Transactional
    public void delete(Long id) {
        SusceptibilityResult result = resultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resultado", id));
        resultRepository.delete(result);
    }
}

