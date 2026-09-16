package pe.edu.utec.atlasrambackend.mapper;

import org.springframework.stereotype.Component;
import pe.edu.utec.atlasrambackend.dto.SusceptibilityResultResponseDTO;
import pe.edu.utec.atlasrambackend.model.SusceptibilityResult;

@Component
public class SusceptibilityResultMapper {

    public SusceptibilityResultResponseDTO toResponse(SusceptibilityResult r) {
        return new SusceptibilityResultResponseDTO(
                r.getId(),
                r.getIsolate().getId(),
                r.getAntibiotic().getCode(),
                r.getAntibiotic().getName(),
                r.getMicValue(),
                r.getDiskDiffusionMm(),
                r.getInterpretation(),
                r.getReportedInterpretation(),
                r.getBreakpointStandard(),
                r.getBreakpointVersion()
        );
    }
}