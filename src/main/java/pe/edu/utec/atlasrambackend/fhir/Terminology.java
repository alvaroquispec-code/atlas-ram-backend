package pe.edu.utec.atlasrambackend.fhir;

import java.util.Map;
import java.util.Optional;

/**
 * Códigos internacionales para el catálogo de Atlas RAM (códigos WHONET).
 *
 * <ul>
 *   <li>LOINC (Regenstrief): prueba de sensibilidad de cada antibiótico en un aislamiento, por CIM
 *       ("X [Susceptibility] by Minimum inhibitory concentration (MIC)") y por difusión en disco
 *       ("X [Susceptibility] by Disk diffusion (KB)").</li>
 *   <li>SNOMED CT: el microorganismo identificado ("X (organism)").</li>
 * </ul>
 *
 * <p>Cada código se verificó uno por uno contra su ficha en loinc.org (versión 2.80) y en buscadores de
 * SNOMED CT International en noviembre de 2026. Un código que no está aquí no se inventa: la exportación
 * manda solo el texto, que FHIR permite.</p>
 */
public final class Terminology {

    public static final String LOINC = "http://loinc.org";
    public static final String SNOMED = "http://snomed.info/sct";

    /** Código LOINC de la prueba por CIM y por disco. */
    public record LoincPair(String mic, String disk) {
    }

    static final Map<String, LoincPair> ANTIBIOTICS = Map.ofEntries(
            Map.entry("AMP", new LoincPair("28-1", "29-9")),
            Map.entry("SAM", new LoincPair("32-3", "33-1")),
            Map.entry("TZP", new LoincPair("412-7", "413-5")),
            Map.entry("OXA", new LoincPair("383-0", "384-8")),
            Map.entry("CZO", new LoincPair("76-0", "77-8")),
            Map.entry("CRO", new LoincPair("141-2", "142-0")),
            Map.entry("CAZ", new LoincPair("133-9", "134-7")),
            Map.entry("FEP", new LoincPair("6644-9", "8272-7")),
            Map.entry("ETP", new LoincPair("35801-0", "35799-6")),
            Map.entry("IPM", new LoincPair("279-0", "280-8")),
            Map.entry("MEM", new LoincPair("6652-2", "6653-0")),
            Map.entry("GEN", new LoincPair("267-5", "268-3")),
            Map.entry("AMK", new LoincPair("12-5", "13-3")),
            Map.entry("CIP", new LoincPair("185-9", "186-7")),
            Map.entry("LVX", new LoincPair("20396-8", "21367-8")),
            Map.entry("SXT", new LoincPair("516-5", "517-3")),
            Map.entry("NIT", new LoincPair("363-2", "364-0")),
            Map.entry("VAN", new LoincPair("524-9", "525-6")),
            Map.entry("LNZ", new LoincPair("29254-0", "29255-7")),
            Map.entry("CLI", new LoincPair("193-3", "194-1")),
            Map.entry("ERY", new LoincPair("233-7", "234-5")));

    static final Map<String, String> ORGANISMS = Map.of(
            "eco", "112283007",   // Escherichia coli
            "kpn", "56415008",    // Klebsiella pneumoniae
            "pae", "52499004",    // Pseudomonas aeruginosa
            "aba", "91288006",    // Acinetobacter baumannii
            "sau", "3092008",     // Staphylococcus aureus
            "efa", "78065002",    // Enterococcus faecalis
            "spn", "9861002",     // Streptococcus pneumoniae
            "sal", "110378009");  // Salmonella enterica

    private Terminology() {
    }

    public static Optional<LoincPair> loinc(String antibioticCode) {
        return Optional.ofNullable(antibioticCode).map(c -> ANTIBIOTICS.get(c.toUpperCase(java.util.Locale.ROOT)));
    }

    public static Optional<String> snomed(String organismCode) {
        return Optional.ofNullable(organismCode).map(c -> ORGANISMS.get(c.toLowerCase(java.util.Locale.ROOT)));
    }
}
