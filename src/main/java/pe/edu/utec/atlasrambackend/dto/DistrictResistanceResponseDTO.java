package pe.edu.utec.atlasrambackend.dto;

public record DistrictResistanceResponseDTO(
        Long districtId,
        String ubigeo,
        String name,
        long totalTested,
        long resistantCount,
        Double resistancePercentage,
        boolean sufficientData
) {}
