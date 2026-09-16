package pe.edu.utec.atlasrambackend.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAntibioticDTO(
        @NotBlank String code,
        @NotBlank String name,
        String antibioticClass
) {}