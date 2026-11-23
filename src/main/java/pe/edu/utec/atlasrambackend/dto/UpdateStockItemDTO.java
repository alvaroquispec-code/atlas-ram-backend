package pe.edu.utec.atlasrambackend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import pe.edu.utec.atlasrambackend.model.VenClass;

import java.math.BigDecimal;

/** Parámetros editables de un ítem. quantityOnOrder: unidades pedidas que aún no llegan. */
public record UpdateStockItemDTO(
        @NotNull @Positive BigDecimal gramsPerUnit,
        @Positive BigDecimal dddGrams,
        @PositiveOrZero BigDecimal unitCost,
        @NotNull VenClass venClass,
        @NotNull @Min(1) @Max(365) Integer reviewPeriodDays,
        @NotNull @Min(0) @Max(365) Integer leadTimeDays,
        @DecimalMin("0.5") @DecimalMax("0.999") BigDecimal serviceLevel,
        @NotNull @PositiveOrZero Integer quantityOnOrder,
        @NotNull Boolean active
) {}
