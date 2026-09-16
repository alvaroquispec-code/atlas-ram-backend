package pe.edu.utec.atlasrambackend.dto;

import pe.edu.utec.atlasrambackend.model.Interpretation;

import java.math.BigDecimal;

public record SusceptibilityResultResponseDTO(
        Long id,
        Long isolateId,
        String antibioticCode,
        String antibioticName,
        BigDecimal micValue,
        Integer diskDiffusionMm,
        Interpretation interpretation,
        Interpretation reportedInterpretation,
        String breakpointStandard,
        String breakpointVersion
) {}