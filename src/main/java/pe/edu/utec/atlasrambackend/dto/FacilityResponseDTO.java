package pe.edu.utec.atlasrambackend.dto;

import java.math.BigDecimal;

public record FacilityResponseDTO(
        Long id,
        String code,
        String name,
        Long districtId,
        String districtName,
        Integer beds,
        BigDecimal occupancyRate
) {}