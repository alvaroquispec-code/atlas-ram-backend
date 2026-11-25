package pe.edu.utec.atlasrambackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Ingreso de un lote. Si había unidades pedidas (quantityOnOrder), se descuentan de lo pendiente. */
public record StockReceiptDTO(
        @NotBlank @Size(max = 50) String lotNumber,
        @NotNull LocalDate expiryDate,
        @NotNull @Positive Integer quantity,
        @NotNull LocalDate receivedOn
) {}
