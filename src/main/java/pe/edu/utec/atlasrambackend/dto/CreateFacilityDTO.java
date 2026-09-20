package pe.edu.utec.atlasrambackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateFacilityDTO(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull Long districtId
) {}