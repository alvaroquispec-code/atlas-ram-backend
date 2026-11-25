package pe.edu.utec.atlasrambackend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Consumo (dispensación) de un día. Se descuenta de los lotes en orden FEFO. */
public record StockConsumptionDTO(
        @NotNull @Positive Integer quantity,
        @NotNull LocalDate consumedOn,
        @Size(max = 500) String note
) {}
