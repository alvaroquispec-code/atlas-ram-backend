package pe.edu.utec.atlasrambackend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateFacilityDTO(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull Long districtId,
        @Positive Integer beds,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("1") BigDecimal occupancyRate
) {}