package pe.edu.utec.atlasrambackend.dto;

import pe.edu.utec.atlasrambackend.model.AwareCategory;

import jakarta.validation.constraints.NotBlank;

public record CreateAntibioticDTO(
        @NotBlank String code,
        @NotBlank String name,
        String antibioticClass,
        AwareCategory awareCategory
) {}