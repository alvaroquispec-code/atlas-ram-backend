package pe.edu.utec.atlasrambackend.dto;

import pe.edu.utec.atlasrambackend.model.AlertStatus;

import java.time.Instant;
import java.time.LocalDate;

public record AlertResponseDTO(
        Long id,
        Long facilityId,
        String facilityName,
        String microorganismName,
        String antibioticName,
        String antibioticClass,
        AlertStatus status,
        LocalDate windowStart,
        LocalDate windowEnd,
        int testedCount,
        int resistantCount,
        double observedPercentage,
        int baselineTested,
        int baselineResistant,
        double baselinePercentage,
        double pValue,
        Instant createdAt,
        Instant updatedAt
) {}
