package pe.edu.utec.atlasrambackend.dto;

import java.time.LocalDate;

public record StockLotResponseDTO(
        Long id,
        Long stockItemId,
        String lotNumber,
        LocalDate expiryDate,
        int quantityReceived,
        int quantityOnHand,
        LocalDate receivedOn,
        boolean expired,
        Integer unitsAtRiskOfExpiry
) {}
