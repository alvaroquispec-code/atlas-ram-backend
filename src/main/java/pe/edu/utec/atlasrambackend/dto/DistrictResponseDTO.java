package pe.edu.utec.atlasrambackend.dto;

public record DistrictResponseDTO(
        Long id,
        String ubigeo,
        String name,
        String province,
        String department
) {}