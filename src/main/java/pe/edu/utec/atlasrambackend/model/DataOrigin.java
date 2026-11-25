package pe.edu.utec.atlasrambackend.model;

/**
 * Procedencia de un dato. Las estadísticas pueden filtrarse por procedencia para que un resultado
 * calculado con datos sintéticos nunca se presente como si fuera de un laboratorio real.
 */
public enum DataOrigin {
    /** Resultados de un laboratorio. */
    REAL,
    /** Datos generados para pruebas y demostración. */
    SYNTHETIC
}
