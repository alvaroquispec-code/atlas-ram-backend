package pe.edu.utec.atlasrambackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.dto.CreateSusceptibilityResultDTO;
import pe.edu.utec.atlasrambackend.dto.SusceptibilityResultResponseDTO;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.exception.DuplicateResourceException;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.mapper.SusceptibilityResultMapper;
import pe.edu.utec.atlasrambackend.model.Antibiotic;
import pe.edu.utec.atlasrambackend.model.Interpretation;
import pe.edu.utec.atlasrambackend.model.Isolate;
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
    private final String standard;
    private final String version;

    public SusceptibilityResultService(SusceptibilityResultRepository resultRepository,
                                       IsolateRepository isolateRepository,
                                       AntibioticRepository antibioticRepository,
                                       InterpretationService interpretationService,
                                       SusceptibilityResultMapper resultMapper,
                                       @Value("${breakpoints.standard}") String standard,
                                       @Value("${breakpoints.version}") String version) {
        this.resultRepository = resultRepository;
        this.isolateRepository = isolateRepository;
        this.antibioticRepository = antibioticRepository;
        this.interpretationService = interpretationService;
        this.resultMapper = resultMapper;
        this.standard = standard;
        this.version = version;
    }

    @Transactional(readOnly = true)
    public List<SusceptibilityResultResponseDTO> findByIsolate(Long isolateId) {
        if (!isolateRepository.existsById(isolateId)) {
            throw new ResourceNotFoundException("Aislamiento", isolateId);
        }
        return resultRepository.findByIsolateId(isolateId).stream()
                .map(resultMapper::toResponse)
                .toList();
    }

    /**
     * La interpretación oficial la calcula el sistema contra el punto de corte vigente.
     * Si no existe punto de corte para esa combinación, el resultado se guarda con la CIM
     * y sin interpretación, en lugar de rechazar el dato del laboratorio.
     */
    @Transactional
    public SusceptibilityResultResponseDTO create(CreateSusceptibilityResultDTO dto) {
        if (dto.micValue() == null && dto.diskDiffusionMm() == null) {
            throw new BusinessRuleException("El resultado necesita una CIM o un diámetro de halo");
        }
        if (resultRepository.existsByIsolateIdAndAntibioticId(dto.isolateId(), dto.antibioticId())) {
            throw new DuplicateResourceException(
                    "Ya existe un resultado para ese aislamiento y antibiótico");
        }
        Isolate isolate = isolateRepository.findById(dto.isolateId())
                .orElseThrow(() -> new ResourceNotFoundException("Aislamiento", dto.isolateId()));
        Antibiotic antibiotic = antibioticRepository.findById(dto.antibioticId())
                .orElseThrow(() -> new ResourceNotFoundException("Antibiótico", dto.antibioticId()));

        SusceptibilityResult result = new SusceptibilityResult();
        result.setIsolate(isolate);
        result.setAntibiotic(antibiotic);
        result.setMicValue(dto.micValue());
        result.setDiskDiffusionMm(dto.diskDiffusionMm());
        result.setReportedInterpretation(dto.reportedInterpretation());
        applyInterpretation(result, isolate, antibiotic, dto);

        return resultMapper.toResponse(resultRepository.save(result));
    }

    private void applyInterpretation(SusceptibilityResult result,
                                     Isolate isolate,
                                     Antibiotic antibiotic,
                                     CreateSusceptibilityResultDTO dto) {
        Interpretation interpretation = interpretationService
                .interpret(dto.micValue(), isolate.getMicroorganism(), antibiotic, standard, version)
                .orElse(dto.reportedInterpretation());
        if (interpretation == null) {
            throw new BusinessRuleException(
                    "No hay punto de corte " + standard + " " + version
                            + " para esa combinación y el laboratorio no reportó interpretación");
        }
        result.setInterpretation(interpretation);
        result.setBreakpointStandard(standard);
        result.setBreakpointVersion(version);
    }

    @Transactional
    public void delete(Long id) {
        SusceptibilityResult result = resultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resultado", id));
        resultRepository.delete(result);
    }
}

