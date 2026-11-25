package pe.edu.utec.atlasrambackend.dto;

/**
 * Normas con las que Atlas RAM interpreta y agrega los resultados, para mostrarlas junto a cada análisis.
 *
 * @param breakpoints        edición de los puntos de corte con que se interpretan las CIM y los halos
 *                           (por ejemplo "CLSI M100-Ed34 (2024)")
 * @param aggregation        cómo se arma el antibiograma acumulado
 * @param expectedPhenotypes tablas de fenotipos esperados que completa el WISCA
 */
public record StandardsResponseDTO(
        String breakpoints,
        String breakpointStandard,
        String breakpointVersion,
        String aggregation,
        String expectedPhenotypes
) {}
