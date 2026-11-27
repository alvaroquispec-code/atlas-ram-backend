package pe.edu.utec.atlasrambackend.access;

/**
 * Colegio profesional que emite el número de colegiatura. Cada uno publica un buscador de colegiados;
 * ninguno ofrece una API abierta, así que la verificación final la hace una persona con el enlace.
 */
public enum ProfessionalBody {
    /** Colegio Médico del Perú. */
    CMP("Colegio Médico del Perú", "https://www.cmp.org.pe/conoce-a-tu-medico/"),
    /** Colegio Tecnólogo Médico del Perú (laboratorio clínico). */
    CTMP("Colegio Tecnólogo Médico del Perú", null),
    /** Colegio de Biólogos del Perú (microbiología). */
    CBP("Colegio de Biólogos del Perú", null),
    /** Colegio Químico Farmacéutico del Perú. */
    CQFP("Colegio Químico Farmacéutico del Perú", null);

    private final String label;
    private final String lookupUrl;

    ProfessionalBody(String label, String lookupUrl) {
        this.label = label;
        this.lookupUrl = lookupUrl;
    }

    public String label() {
        return label;
    }

    /** Buscador público de colegiados, o null si no lo tenemos verificado. */
    public String lookupUrl() {
        return lookupUrl;
    }
}
