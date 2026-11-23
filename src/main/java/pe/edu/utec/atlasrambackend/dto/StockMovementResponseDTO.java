package pe.edu.utec.atlasrambackend.dto;

import pe.edu.utec.atlasrambackend.model.MovementType;

import java.time.LocalDate;

public record StockMovementResponseDTO(
        Long id,
        Long stockItemId,
        Long lotId,
        String lotNumber,
        MovementType type,
        int quantity,
        LocalDate movementDate,
        String note
) {}
