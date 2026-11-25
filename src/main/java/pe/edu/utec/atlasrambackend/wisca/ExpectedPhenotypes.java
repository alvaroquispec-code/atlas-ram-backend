package pe.edu.utec.atlasrambackend.wisca;

import pe.edu.utec.atlasrambackend.model.Interpretation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Fenotipos esperados de EUCAST, para los pares germen–antibiótico que el laboratorio no prueba.
 *
 * EUCAST abandonó el término "resistencia intrínseca" y lo reemplazó por:
 *   - Fenotipo resistente esperado (Expected Resistant Phenotypes v1.2, 13/01/2023): ≥ 90 % de la especie es
 *     resistente; se puede informar R sin probar (Klebsiella frente a ampicilina, P. aeruginosa frente a
 *     ceftriaxona, Enterococcus faecalis frente a las cefalosporinas).
 *   - Fenotipo sensible esperado (Expected Susceptible Phenotypes v1.1, 25/03/2022): la resistencia es
 *     excepcional (≈ 99 % sin mecanismos adquiridos); se puede informar S sin probar (S. pneumoniae frente a
 *     los carbapenémicos, S. aureus frente a vancomicina y linezolid).
 * https://www.eucast.org/expert_rules_and_expected_phenotypes/expected_phenotypes
 *
 * Solo se incluyen los gérmenes y antibióticos del catálogo de Atlas RAM. Lo que no figura en ninguna de las
 * dos tablas queda "sin datos": no se inventa. Por ejemplo, EUCAST da como sensible esperado a los
 * carbapenémicos solo a Salmonella Typhi, no a todas las Salmonella enterica, así que ese par queda sin datos.
 *
 * Un resultado medido nunca se sobrescribe: si el laboratorio informó otra cosa, manda lo medido (EUCAST pide
 * verificar esos casos, no corregirlos en silencio).
 */
public final class ExpectedPhenotypes {

    /**
     * Expected Resistant Phenotypes v1.2 (verificado contra el PDF oficial).
     * Macrólidos: EUCAST los da como resistentes esperados en Enterobacterales "con algunas excepciones"
     * (nota 1: azitromicina en fiebre tifoidea, eritromicina en diarrea del viajero), así que no se aplican a
     * E. coli ni a Salmonella. En E. faecalis la columna es "sulfonamidas", no trimetoprima-sulfametoxazol,
     * así que SXT queda sin regla.
     */
    static final Map<String, Set<String>> EXPECTED_RESISTANT = Map.of(
            // Tabla 1 (encabezado): glucopéptidos, lincosamidas y oxazolidinonas.
            "eco", Set.of("VAN", "CLI", "LNZ"),
            "sal", Set.of("VAN", "CLI", "LNZ"),
            // Tabla 1, regla 1.7 (Klebsiella pneumoniae complex): ampicilina; macrólidos por el encabezado.
            "kpn", Set.of("VAN", "ERY", "CLI", "LNZ", "AMP"),
            // Tabla 2 (encabezado): cefalosporinas de 1.ª y 2.ª generación, glucopéptidos, macrólidos,
            // lincosamidas y oxazolidinonas.
            // Regla 2.7 (P. aeruginosa): ampicilina, ampicilina-sulbactam, ceftriaxona, ertapenem.
            "pae", Set.of("CZO", "VAN", "ERY", "CLI", "LNZ", "AMP", "SAM", "CRO", "ETP"),
            // Regla 2.1 (A. baumannii): ampicilina, ceftriaxona, ertapenem. Ampicilina-sulbactam NO (nota 1:
            // el sulbactam tiene actividad propia contra esta especie).
            "aba", Set.of("CZO", "VAN", "ERY", "CLI", "LNZ", "AMP", "CRO", "ETP"),
            // Tabla 4, regla 4.5 (S. aureus): ceftazidima.
            "sau", Set.of("CAZ"),
            // Regla 4.6 (Streptococcus spp.): ceftazidima y aminoglucósidos (resistencia de bajo nivel).
            "spn", Set.of("CAZ", "GEN", "AMK"),
            // Regla 4.7 (E. faecalis): ceftazidima, demás cefalosporinas, aminoglucósidos (bajo nivel),
            // macrólidos, clindamicina.
            "efa", Set.of("CZO", "CRO", "CAZ", "FEP", "GEN", "AMK", "ERY", "CLI"));

    /** Expected Susceptible Phenotypes v1.1 (verificado contra el PDF oficial). */
    static final Map<String, Set<String>> EXPECTED_SUSCEPTIBLE = Map.of(
            // Regla 2.1: S. aureus resistente a vancomicina o linezolid es un fenotipo inusual.
            "sau", Set.of("VAN", "LNZ"),
            // Regla 2.4: S. pneumoniae resistente a carbapenémicos, vancomicina o linezolid es inusual.
            "spn", Set.of("ETP", "IPM", "MEM", "VAN", "LNZ"),
            // Reglas 2.6 y 2.7: Enterococcus resistente a linezolid; E. faecalis resistente a ampicilina.
            "efa", Set.of("LNZ", "AMP"));

    public enum Source { MEASURED, EXPECTED_SUSCEPTIBLE, EXPECTED_RESISTANT, UNKNOWN }

    private ExpectedPhenotypes() {
    }

    /** Completa los antibióticos no informados con el fenotipo esperado; lo medido se conserva. */
    public static Map<String, Interpretation> apply(String microorganismCode, Map<String, Interpretation> measured) {
        Set<String> resistant = EXPECTED_RESISTANT.getOrDefault(microorganismCode, Set.of());
        Set<String> susceptible = EXPECTED_SUSCEPTIBLE.getOrDefault(microorganismCode, Set.of());
        if (resistant.isEmpty() && susceptible.isEmpty()) {
            return measured;
        }
        Map<String, Interpretation> completed = new HashMap<>(measured);
        resistant.forEach(code -> completed.putIfAbsent(code, Interpretation.RESISTANT));
        susceptible.forEach(code -> completed.putIfAbsent(code, Interpretation.SUSCEPTIBLE));
        return completed;
    }

    /** De dónde sale el dato de un antibiótico para un germen cuando el laboratorio no lo probó. */
    public static Source expected(String microorganismCode, String antibioticCode) {
        if (EXPECTED_SUSCEPTIBLE.getOrDefault(microorganismCode, Set.of()).contains(antibioticCode)) {
            return Source.EXPECTED_SUSCEPTIBLE;
        }
        if (EXPECTED_RESISTANT.getOrDefault(microorganismCode, Set.of()).contains(antibioticCode)) {
            return Source.EXPECTED_RESISTANT;
        }
        return Source.UNKNOWN;
    }
}
