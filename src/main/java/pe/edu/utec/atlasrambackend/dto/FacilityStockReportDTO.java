package pe.edu.utec.atlasrambackend.dto;

import pe.edu.utec.atlasrambackend.logistics.StockState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record FacilityStockReportDTO(
        Long facilityId,
        String facilityName,
        LocalDate asOf,
        int itemCount,
        Map<StockState, Long> itemsBySismedState,
        int itemsToOrder,
        BigDecimal suggestedOrderValue,
        int itemsWithExpiryRisk,
        BigDecimal valueAtRiskOfExpiry,
        int itemsUnderOutbreakPressure,
        int activeAlerts,
        List<StockItemStatusDTO> items,
        String method
) {}
