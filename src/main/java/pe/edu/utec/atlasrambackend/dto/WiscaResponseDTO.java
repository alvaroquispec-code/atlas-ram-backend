package pe.edu.utec.atlasrambackend.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * WISCA bayesiano: probabilidad de que cada régimen empírico cubra la infección de un síndrome.
 * Porcentajes de 0 a 100; intervalos creíbles al 95 % (percentiles 2,5 y 97,5 de la simulación).
 */
public record WiscaResponseDTO(
        String syndrome,
        String syndromeLabel,
        String specimen,
        LocalDate from,
        LocalDate to,
        /** Primeros aislamientos (por paciente y especie) del tipo de muestra en el periodo. */
        int isolates,
        /** Con menos de 30 aislamientos las estimaciones son muy inciertas. */
        boolean sufficientData,
        double targetCoverage,
        int simulationDraws,
        List<Pathogen> pathogens,
        /** Ordenados de mayor a menor cobertura mediana. */
        List<Regimen> regimens,
        String method
) {
    public record Pathogen(String name, int isolates, double frequency) {}

    public record Regimen(
            String code,
            List<String> antibioticNames,
            double coverageMedian,
            double coverageMean,
            double credibleLow,
            double credibleHigh,
            /** P(cobertura ≥ targetCoverage). */
            double probabilityAboveTarget,
            /** P(este régimen es el de mayor cobertura entre los comparados). */
            double probabilityBest,
            /** Frecuencia de los gérmenes sin datos para el régimen (contados como no cubiertos). */
            double untestedFrequency,
            /** Sin pruebas y sin fenotipo esperado de EUCAST: se cuentan como no cubiertos. */
            List<String> untestedPathogens,
            /** Sin pruebas, pero sensibles esperados según EUCAST: se cuentan como cubiertos. */
            List<String> expectedSusceptiblePathogens,
            /** Sin pruebas, pero resistentes esperados según EUCAST (antes "resistencia intrínseca"). */
            List<String> expectedResistantPathogens,
            List<PathogenCoverage> byPathogen
    ) {}

    public record PathogenCoverage(String name, int evaluated, int covered, Double percentage) {}
}
