package pe.edu.utec.atlasrambackend.dto;

import pe.edu.utec.atlasrambackend.logistics.AbcClass;
import pe.edu.utec.atlasrambackend.logistics.DemandPattern;
import pe.edu.utec.atlasrambackend.logistics.ForecastMethod;
import pe.edu.utec.atlasrambackend.logistics.StockState;
import pe.edu.utec.atlasrambackend.model.AwareCategory;
import pe.edu.utec.atlasrambackend.model.VenClass;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Situación de un ítem en cuatro bloques:
 * 1) disponibilidad SISMED (CPMA, MED, estado); 2) demanda (patrón SBC, método, pronóstico);
 * 3) reposición (R, S) con stock de seguridad por bootstrap; 4) riesgos (vencimiento y brote).
 */
public record StockItemStatusDTO(
        Long itemId,
        Long antibioticId,
        String antibioticCode,
        String antibioticName,
        AwareCategory awareCategory,
        String presentation,
        VenClass venClass,
        AbcClass abcClass,
        Integer abcVenCategory,
        BigDecimal unitCost,

        int availableUnits,
        int expiredUnitsOnHand,
        int quantityOnOrder,
        double cpma,
        Double monthsOfStock,
        StockState sismedState,
        String sismedStateLabel,

        double dailyRate,
        Double coverageDays,
        DemandPattern demandPattern,
        Double adi,
        Double cv2,
        ForecastMethod forecastMethod,
        Double forecastPerMonth,
        int historyMonths,

        int reviewPeriodDays,
        int leadTimeDays,
        double serviceLevel,
        Double expectedProtectionDemand,
        Integer safetyStock,
        Integer orderUpTo,
        int inventoryPosition,
        Integer suggestedOrder,
        BigDecimal suggestedOrderValue,

        int unitsAtRiskOfExpiry,
        BigDecimal valueAtRiskOfExpiry,
        LocalDate nextExpiry,
        boolean outbreakPressure,
        String outbreakReason,

        List<MonthlyConsumption> monthlyConsumption
) {
    public record MonthlyConsumption(LocalDate month, int units) {
    }
}
