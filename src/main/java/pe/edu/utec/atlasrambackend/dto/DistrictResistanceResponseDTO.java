package pe.edu.utec.atlasrambackend.dto;

import pe.edu.utec.atlasrambackend.model.BreakpointSite;

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
        Double resistanceCiHigh,
        /** Punto de corte por sitio usado (solo pares como S. pneumoniae × ceftriaxona); null en el resto. */
        BreakpointSite breakpointSite
) {}
