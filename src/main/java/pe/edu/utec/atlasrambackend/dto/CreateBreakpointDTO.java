package pe.edu.utec.atlasrambackend.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;


import java.math.BigDecimal;


public record CreateBreakpointDTO(
        @NotNull Long microorganismId,
        @NotNull Long antibioticId,
        @NotBlank String standard,
        @NotBlank String version,
        @Positive BigDecimal susceptibleMax,
        @Positive BigDecimal resistantMin
) {}


