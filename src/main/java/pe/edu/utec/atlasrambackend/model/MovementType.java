package pe.edu.utec.atlasrambackend.model;

/** Tipos de movimiento del kardex. Solo CONSUMPTION alimenta las series de demanda. */
public enum MovementType {
    /** Ingreso de un lote. */
    RECEIPT,
    /** Dispensación a pacientes. */
    CONSUMPTION,
    /** Baja: vencido, deteriorado o perdido. No es demanda, pero sí es pérdida. */
    WRITE_OFF
}
