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
        BreakpointSite breakpointSite,
        /**
         * Estimación con pooling parcial (Bayes empírico beta-binomial): se acerca a la media de la red cuando
         * el distrito tiene pocos aislamientos. Es un dato adicional: no reemplaza resistancePercentage ni la
         * regla de 30 de CLSI M39. Null si el distrito no tiene datos o la red no alcanza para estimar.
         */
        Double pooledResistancePercentage,
        Double pooledCiLow,
        Double pooledCiHigh
) {}
