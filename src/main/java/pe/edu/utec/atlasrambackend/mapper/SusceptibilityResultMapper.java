package pe.edu.utec.atlasrambackend.mapper;

import org.springframework.stereotype.Component;
import pe.edu.utec.atlasrambackend.dto.SusceptibilityResultResponseDTO;
import pe.edu.utec.atlasrambackend.model.MicQualifier;
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
                r.getMicQualifier(),
                micDisplay(r),
                r.getDiskDiffusionMm(),
                r.getInterpretation(),
                r.getReportedInterpretation(),
                r.getInterpretationSource(),
                r.getBreakpointStandard(),
                r.getBreakpointVersion()
        );
    }

    private static String micDisplay(SusceptibilityResult r) {
        if (r.getMicValue() == null) {
            return null;
        }
        String value = r.getMicValue().stripTrailingZeros().toPlainString();
        return r.getMicQualifier() == null || r.getMicQualifier() == MicQualifier.EQ
                ? value
                : r.getMicQualifier().symbol() + value;
    }
}
