package pe.edu.utec.atlasrambackend.model;

/**
 * Sitio de infección al que se aplica un punto de corte.
 *
 * CLSI M100 da dos puntos de corte para algunos pares germen–antibiótico según dónde está la infección, porque el
 * antibiótico llega peor al líquido cefalorraquídeo. Ejemplo: Streptococcus pneumoniae frente a ceftriaxona,
 * meningitis S ≤ 0,5 / I 1 / R ≥ 2 µg/mL; otras infecciones S ≤ 1 / I 2 / R ≥ 4 µg/mL. La misma CIM de 1 µg/mL
 * es sensible en una neumonía e intermedia en una meningitis.
 *
 * ANY = el punto de corte no depende del sitio (la gran mayoría).
 */
public enum BreakpointSite {
    ANY, MENINGITIS, NON_MENINGITIS;

    /** El sitio que corresponde a una muestra: el líquido cefalorraquídeo es meningitis; el resto, no. */
    public static BreakpointSite forSpecimen(Specimen specimen) {
        return specimen == Specimen.CSF ? MENINGITIS : NON_MENINGITIS;
    }
}
