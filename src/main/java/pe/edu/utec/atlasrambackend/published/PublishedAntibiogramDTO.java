package pe.edu.utec.atlasrambackend.published;

import java.time.LocalDate;
import java.util.List;

/**
 * Antibiograma REAL publicado (agregado), con su fuente y licencia. Cada fila trae, para comparar, la
 * resistencia que calcula Atlas RAM con sus propios datos para el mismo par y el mismo tipo de muestra.
 */
public record PublishedAntibiogramDTO(
        String code,
        String title,
        String citation,
        String url,
        String license,
        String setting,
        String specimenType,
        LocalDate periodStart,
        LocalDate periodEnd,
        String breakpointStandard,
        List<Row> rows
) {
    /**
     * resistancePercentage y su intervalo de Wilson al 95 % salen de la fuente. atlas*: primer aislamiento
     * por paciente en Atlas RAM con el mismo tipo de muestra (null si Atlas no tiene ese par).
     */
    public record Row(
            Long microorganismId,
            Long antibioticId,
            String organismLabel,
            String antibioticName,
            int tested,
            int resistant,
            double resistancePercentage,
            double resistanceCiLow,
            double resistanceCiHigh,
            Long atlasTested,
            Double atlasResistancePercentage
    ) {}
}
