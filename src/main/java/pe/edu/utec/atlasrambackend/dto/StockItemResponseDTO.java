package pe.edu.utec.atlasrambackend.dto;

import pe.edu.utec.atlasrambackend.model.AwareCategory;
import pe.edu.utec.atlasrambackend.model.VenClass;

import java.math.BigDecimal;

public record StockItemResponseDTO(
        Long id,
        Long facilityId,
        Long antibioticId,
        String antibioticCode,
        String antibioticName,
        AwareCategory awareCategory,
        String presentation,
        BigDecimal gramsPerUnit,
        BigDecimal dddGrams,
        BigDecimal unitCost,
        VenClass venClass,
        int reviewPeriodDays,
        int leadTimeDays,
        BigDecimal serviceLevel,
        int quantityOnOrder,
        boolean active
) {}
