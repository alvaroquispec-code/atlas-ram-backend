package pe.edu.utec.atlasrambackend.dto;

public record FacilityResponseDTO(
        Long id,
        String code,
        String name,
        Long districtId,
        String districtName
) {}