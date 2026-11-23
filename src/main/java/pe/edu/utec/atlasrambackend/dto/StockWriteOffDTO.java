package pe.edu.utec.atlasrambackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Baja de un lote (vencido, deteriorado, perdido). Sin quantity se da de baja todo el saldo. */
public record StockWriteOffDTO(
        @Positive Integer quantity,
        @NotNull LocalDate writtenOffOn,
        @NotBlank @Size(max = 500) String reason
) {}
