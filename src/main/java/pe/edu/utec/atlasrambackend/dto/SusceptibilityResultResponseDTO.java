package pe.edu.utec.atlasrambackend.dto;

import pe.edu.utec.atlasrambackend.model.Interpretation;
import pe.edu.utec.atlasrambackend.model.InterpretationSource;
import pe.edu.utec.atlasrambackend.model.MicQualifier;

import java.math.BigDecimal;

public record SusceptibilityResultResponseDTO(
        Long id,
        Long isolateId,
        String antibioticCode,
        String antibioticName,
        BigDecimal micValue,
        MicQualifier micQualifier,
        /** CIM lista para mostrar, por ejemplo "<=0.25" o "2". */
        String micDisplay,
        Integer diskDiffusionMm,
        Interpretation interpretation,
        Interpretation reportedInterpretation,
        InterpretationSource interpretationSource,
        String breakpointStandard,
        String breakpointVersion
) {}