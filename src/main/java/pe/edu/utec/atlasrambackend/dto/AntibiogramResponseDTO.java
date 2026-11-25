package pe.edu.utec.atlasrambackend.dto;

import pe.edu.utec.atlasrambackend.model.BreakpointSite;

/**
 * Una fila del antibiograma acumulado (CLSI M39): primer aislamiento por paciente y especie.
 * Los porcentajes van de 0 a 100; los intervalos son de Wilson al 95 %.
 *
 * Los cinco primeros campos son los de siempre, para no romper a los clientes que ya los leen.
 */
public record AntibiogramResponseDTO(
        String microorganismName,
        String antibioticName,
        long totalTested,
        long resistantCount,
        double resistancePercentage,
        Long microorganismId,
        Long antibioticId,
        long susceptibleCount,
        long intermediateCount,
        double susceptiblePercentage,
        double susceptibleCiLow,
        double susceptibleCiHigh,
        double resistanceCiLow,
        double resistanceCiHigh,
        /** n ≥ 30: el mínimo de CLSI M39 para que el porcentaje sea interpretable. */
        boolean sufficientData,
        /** Aislamientos repetidos del mismo paciente que se excluyeron de esta fila. */
        long repeatIsolatesExcluded,
        /** Porcentaje de aislamientos con paciente identificado: sin él no se pueden excluir repetidos. */
        double patientIdentifiedPercentage,
        /** Porcentaje de los aislamientos de la fila que vienen de un laboratorio real (no sintéticos). */
        double realDataPercentage,
        /**
         * Solo en pares con punto de corte por sitio de infección (S. pneumoniae × ceftriaxona y cefepima):
         * MENINGITIS o NON_MENINGITIS. Ese par sale en dos filas; cada una aplica su punto de corte a la CIM
         * de TODOS los primeros aislamientos. null en el resto.
         */
        BreakpointSite breakpointSite
) {}
