package pe.edu.utec.atlasrambackend.dto;

/** Resistencia de un par microorganismo-antibiótico en un distrito (primer aislamiento por paciente). */
public record DistrictResistanceResponseDTO(
        Long districtId,
        String ubigeo,
        String name,
        long totalTested,
        long resistantCount,
        Double resistancePercentage,
        boolean sufficientData,
        /** Intervalo de Wilson al 95 %; null cuando no hay datos suficientes, igual que el porcentaje. */
        Double resistanceCiLow,
        Double resistanceCiHigh
) {}
