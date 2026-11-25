package pe.edu.utec.atlasrambackend.fhir;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TerminologyTest {

    /** Los 21 antibióticos y 8 microorganismos del catálogo de Atlas RAM. */
    private static final List<String> ANTIBIOTICS = List.of("AMP", "SAM", "TZP", "OXA", "CZO", "CRO", "CAZ", "FEP",
            "ETP", "IPM", "MEM", "GEN", "AMK", "CIP", "LVX", "SXT", "NIT", "VAN", "LNZ", "CLI", "ERY");
    private static final List<String> ORGANISMS = List.of("eco", "kpn", "pae", "aba", "sau", "efa", "spn", "sal");

    @Test
    void todoElCatalogoTieneSusCodigos() {
        for (String code : ANTIBIOTICS) {
            Terminology.LoincPair pair = Terminology.loinc(code).orElseThrow();
            // Formato LOINC: número, guion y dígito verificador.
            assertThat(pair.mic().matches("\\d{1,6}-\\d")).isTrue();
            assertThat(pair.disk().matches("\\d{1,6}-\\d")).isTrue();
            assertThat(pair.mic()).isNotEqualTo(pair.disk());
        }
        for (String code : ORGANISMS) {
            assertThat(Terminology.snomed(code).orElseThrow().matches("\\d{6,18}")).isTrue();
        }
    }

    @Test
    void codigosClaveVerificados() {
        assertThat(Terminology.loinc("MEM").orElseThrow().mic()).isEqualTo("6652-2");
        assertThat(Terminology.loinc("amk").orElseThrow().mic()).isEqualTo("12-5");
        assertThat(Terminology.snomed("KPN").orElseThrow()).isEqualTo("56415008");
        assertThat(Terminology.loinc("XYZ")).isEmpty();
        assertThat(Terminology.snomed(null)).isEmpty();
    }
}
