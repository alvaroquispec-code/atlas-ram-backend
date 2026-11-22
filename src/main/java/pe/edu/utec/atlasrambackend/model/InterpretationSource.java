package pe.edu.utec.atlasrambackend.model;

/** De dónde viene la interpretación S/I/R de un resultado. */
public enum InterpretationSource {
    /** Calculada por el sistema contra un punto de corte (estándar y versión quedan registrados). */
    CALCULATED,
    /** Informada por el laboratorio: no había punto de corte o la CIM no permitía decidir. */
    LAB_REPORTED
}
