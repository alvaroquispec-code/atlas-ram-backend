package pe.edu.utec.atlasrambackend.model;

/**
 * Criticidad clínica del ítem (análisis VEN de la OMS/MSH): su falta pone en riesgo la vida (VITAL),
 * afecta el tratamiento de infecciones frecuentes (ESSENTIAL) o tiene alternativas (NON_ESSENTIAL).
 * La define el comité farmacoterapéutico de cada establecimiento.
 */
public enum VenClass {
    VITAL,
    ESSENTIAL,
    NON_ESSENTIAL
}
