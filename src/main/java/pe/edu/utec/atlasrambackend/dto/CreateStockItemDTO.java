package pe.edu.utec.atlasrambackend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import pe.edu.utec.atlasrambackend.model.VenClass;

import java.math.BigDecimal;

/**
 * Alta de un ítem de inventario. reviewPeriodDays (R) y leadTimeDays (L) son opcionales: por defecto
 * 30 y 15 días, valores iniciales que el establecimiento debe reemplazar por los suyos.
 * dddGrams es la DDD de la OMS para la vía de la presentación (índice ATC/DDD).
 */
public record CreateStockItemDTO(
        @NotNull Long facilityId,
        @NotNull Long antibioticId,
        @NotBlank @Size(max = 100) String presentation,
        @NotNull @Positive BigDecimal gramsPerUnit,
        @Positive BigDecimal dddGrams,
        @PositiveOrZero BigDecimal unitCost,
        @NotNull VenClass venClass,
        @Min(1) @Max(365) Integer reviewPeriodDays,
        @Min(0) @Max(365) Integer leadTimeDays,
        @DecimalMin("0.5") @DecimalMax("0.999") BigDecimal serviceLevel
) {}
