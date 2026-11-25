package pe.edu.utec.atlasrambackend.logistics;

/**
 * Patrón de demanda según Syntetos, Boylan y Croston (2005): cruza el intervalo medio entre demandas
 * (ADI, corte 1.32) con la variabilidad del tamaño de las demandas no nulas (CV², corte 0.49).
 */
public enum DemandPattern {
    /** Demanda casi todos los meses y de tamaño estable. */
    SMOOTH,
    /** Casi todos los meses, pero de tamaño muy variable. */
    ERRATIC,
    /** Muchos meses en cero; tamaño estable cuando hay demanda. */
    INTERMITTENT,
    /** Muchos meses en cero y tamaño variable: el caso más difícil. */
    LUMPY,
    /** Historia insuficiente para clasificar (menos de 6 meses o menos de 2 meses con demanda). */
    INSUFFICIENT_HISTORY
}
