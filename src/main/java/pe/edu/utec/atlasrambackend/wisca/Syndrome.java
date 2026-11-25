package pe.edu.utec.atlasrambackend.wisca;

import pe.edu.utec.atlasrambackend.model.Specimen;

import java.util.List;

/**
 * Síndromes del WISCA y su muestra de referencia.
 *
 * Limitación declarada: el sistema no recibe diagnósticos, así que el síndrome se aproxima por el tipo
 * de muestra (un urocultivo positivo no siempre es una infección urinaria). Los regímenes por defecto
 * son candidatos habituales en las guías para comparar, NO recomendaciones de tratamiento.
 */
public enum Syndrome {

    BACTEREMIA("Bacteriemia", Specimen.BLOOD,
            List.of("CRO", "FEP", "TZP", "MEM", "TZP+AMK", "FEP+VAN", "MEM+VAN")),
    URINARY("Infección urinaria", Specimen.URINE,
            List.of("NIT", "SXT", "CIP", "CRO", "AMK", "ETP", "MEM")),
    RESPIRATORY("Infección respiratoria", Specimen.RESPIRATORY,
            List.of("CRO", "FEP", "TZP", "MEM", "FEP+AMK", "TZP+AMK", "MEM+LNZ")),
    SKIN_SOFT_TISSUE("Piel y partes blandas", Specimen.WOUND,
            List.of("CZO", "CLI", "SXT", "TZP", "TZP+VAN")),
    MENINGITIS("Meningitis", Specimen.CSF,
            List.of("CRO", "CRO+VAN", "MEM"));

    private final String label;
    private final Specimen specimen;
    private final List<String> defaultRegimens;

    Syndrome(String label, Specimen specimen, List<String> defaultRegimens) {
        this.label = label;
        this.specimen = specimen;
        this.defaultRegimens = defaultRegimens;
    }

    public String label() {
        return label;
    }

    public Specimen specimen() {
        return specimen;
    }

    public List<String> defaultRegimens() {
        return defaultRegimens;
    }
}
