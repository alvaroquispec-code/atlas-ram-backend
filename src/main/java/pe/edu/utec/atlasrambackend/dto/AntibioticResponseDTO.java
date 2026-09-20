package pe.edu.utec.atlasrambackend.dto;

public record AntibioticResponseDTO(
        Long id,
        String code,
        String name,
        String antibioticClass
) {}