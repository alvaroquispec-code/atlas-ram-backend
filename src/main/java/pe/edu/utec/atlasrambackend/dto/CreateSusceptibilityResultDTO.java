package pe.edu.utec.atlasrambackend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import pe.edu.utec.atlasrambackend.model.Interpretation;
import pe.edu.utec.atlasrambackend.model.MicQualifier;

import java.math.BigDecimal;

public record CreateSusceptibilityResultDTO(
        @NotNull Long isolateId,
        @NotNull Long antibioticId,
        @Positive BigDecimal micValue,
        MicQualifier micQualifier,
        Integer diskDiffusionMm,
        Interpretation reportedInterpretation
) {}