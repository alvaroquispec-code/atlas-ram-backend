package pe.edu.utec.atlasrambackend.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateMicroorganismDTO(
        @NotBlank String code,
        @NotBlank String genus,
        String species,
        String gramStain
) {}