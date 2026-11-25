package pe.edu.utec.atlasrambackend.model;

/**
 * Servicio donde estaba el paciente cuando se tomó la muestra.
 *
 * CLSI M39 recomienda, además del antibiograma general, antibiogramas estratificados por unidad (sobre todo
 * UCI), porque la resistencia en cuidados intensivos suele ser mucho mayor y el tratamiento empírico de esos
 * pacientes no debe guiarse por el promedio del hospital. El INS lo exige en el registro (ver CreateIsolateDTO);
 * en las cargas masivas es opcional porque no todos los laboratorios lo informan.
 */
public enum Ward {
    OUTPATIENT, EMERGENCY, INPATIENT, ICU;

    /**
     * Código del "Tipo de localización" (TYPE_WARD) que pide el INS para WHONET-INS: IN = hospitalizado,
     * OUT = ambulatorio, OTH = otros. Emergencia no tiene un código asignado en la tabla del INS: queda null
     * hasta confirmarlo con la Red Nacional de Laboratorios, en vez de suponer IN, OUT u OTH.
     */
    public String insLocationType() {
        return switch (this) {
            case ICU, INPATIENT -> "IN";
            case OUTPATIENT -> "OUT";
            case EMERGENCY -> null;
        };
    }
}
