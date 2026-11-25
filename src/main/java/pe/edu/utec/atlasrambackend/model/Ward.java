package pe.edu.utec.atlasrambackend.model;

/**
 * Servicio donde estaba el paciente cuando se tomó la muestra.
 *
 * CLSI M39 recomienda, además del antibiograma general, antibiogramas estratificados por unidad (sobre todo
 * UCI), porque la resistencia en cuidados intensivos suele ser mucho mayor y el tratamiento empírico de esos
 * pacientes no debe guiarse por el promedio del hospital. Es opcional: no todos los laboratorios lo informan.
 */
public enum Ward {
    OUTPATIENT, EMERGENCY, INPATIENT, ICU
}
