package pe.edu.utec.atlasrambackend.dto;

public record MicroorganismResponseDTO(
        Long id,
        String code,
        String genus,
        String species,
        String gramStain
) {}