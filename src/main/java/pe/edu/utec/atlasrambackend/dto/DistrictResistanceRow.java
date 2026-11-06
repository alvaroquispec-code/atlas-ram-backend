package pe.edu.utec.atlasrambackend.dto;

public record DistrictResistanceRow(
        Long districtId,
        long totalTested,
        long resistantCount
) {}
