package pe.edu.utec.atlasrambackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateDistrictDTO(
        @NotBlank @Pattern(regexp = "\\d{6}") String ubigeo,
        @NotBlank String name,
        String province,
        String department
) {}