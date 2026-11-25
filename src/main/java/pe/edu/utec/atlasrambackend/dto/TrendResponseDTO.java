package pe.edu.utec.atlasrambackend.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Evolución de la resistencia de un par microorganismo-antibiótico, periodo a periodo, con su prueba
 * de tendencia. Porcentajes de 0 a 100; intervalos de Wilson al 95 %.
 */
public record TrendResponseDTO(
        Long microorganismId,
        String microorganismName,
        Long antibioticId,
        String antibioticName,
        String interval,
        List<Point> points,
        /** Null si hay menos de dos periodos con datos o la resistencia es 0 % o 100 % en todos. */
        Trend trend
) {
    public record Point(
            LocalDate periodStart,
            long totalTested,
            long resistantCount,
            Double resistancePercentage,
            Double resistanceCiLow,
            Double resistanceCiHigh,
            Double susceptiblePercentage
    ) {}

    public record Trend(
            /** Estadístico z de Cochran-Armitage: positivo = la resistencia sube. */
            double z,
            double pValue,
            /** Cambio estimado de la resistencia, en puntos porcentuales por año. */
            double changePerYear,
            /** INCREASING o DECREASING si p < 0,05; si no, NO_SIGNIFICANT_TREND. */
            String direction,
            int periodsWithData,
            long totalTested
    ) {}
}
