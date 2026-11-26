package pe.edu.utec.atlasrambackend.interpretation;

import java.util.Map;
import java.util.Set;

/**
 * Resistencia intrínseca (natural) según CLSI M100, apéndice B: la especie es resistente por naturaleza,
 * así que el antibiótico no se prueba ni se informa como opción. Por ejemplo, E. coli frente a vancomicina:
 * la vancomicina no atraviesa la membrana externa de los gramnegativos.
 *
 * Solo se incluyen los pares del catálogo (códigos WHONET) que el apéndice B lista sin excepciones.
 */
public final class IntrinsicResistance {

    /** Agentes para grampositivos sin actividad en bacilos gramnegativos (apéndice B, tablas de gramnegativos). */
    private static final Set<String> GRAM_POSITIVE_AGENTS = Set.of("OXA", "VAN", "LNZ", "CLI", "ERY");

    private static final Map<String, Set<String>> BY_SPECIES = Map.of(
            // Enterobacterales
            "eco", GRAM_POSITIVE_AGENTS,
            "kpn", union(GRAM_POSITIVE_AGENTS, Set.of("AMP")),
            "sal", GRAM_POSITIVE_AGENTS,
            // No fermentadores
            "pae", union(GRAM_POSITIVE_AGENTS, Set.of("AMP", "SAM", "CRO", "ETP", "SXT")),
            "aba", union(GRAM_POSITIVE_AGENTS, Set.of("AMP", "ETP")),
            // Enterococcus faecalis: cefalosporinas, clindamicina y trimetoprima/sulfametoxazol
            "efa", Set.of("CZO", "CRO", "CAZ", "FEP", "CLI", "SXT"));

    private IntrinsicResistance() {
    }

    public static boolean isIntrinsic(String microorganismCode, String antibioticCode) {
        if (microorganismCode == null || antibioticCode == null) {
            return false;
        }
        return BY_SPECIES.getOrDefault(microorganismCode, Set.of()).contains(antibioticCode);
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        var all = new java.util.HashSet<>(a);
        all.addAll(b);
        return Set.copyOf(all);
    }
}
