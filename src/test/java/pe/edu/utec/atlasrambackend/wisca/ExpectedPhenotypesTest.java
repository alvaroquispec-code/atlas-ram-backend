package pe.edu.utec.atlasrambackend.wisca;

import org.junit.jupiter.api.Test;
import pe.edu.utec.atlasrambackend.model.Interpretation;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExpectedPhenotypesTest {

    @Test
    void completaLoQueFaltaConLasTablasDeEucast() {
        Map<String, Interpretation> pae = ExpectedPhenotypes.apply("pae", Map.of("MEM", Interpretation.SUSCEPTIBLE));

        assertThat(pae).containsEntry("CRO", Interpretation.RESISTANT)
                .containsEntry("ETP", Interpretation.RESISTANT)
                .containsEntry("MEM", Interpretation.SUSCEPTIBLE)
                .doesNotContainKey("CIP");
    }

    @Test
    void enterococcusFaecalisEsResistenteEsperadoALasCefalosporinasYSensibleEsperadoAAmpicilina() {
        Map<String, Interpretation> efa = ExpectedPhenotypes.apply("efa", Map.of());

        assertThat(efa).containsEntry("CRO", Interpretation.RESISTANT)
                .containsEntry("FEP", Interpretation.RESISTANT)
                .containsEntry("AMP", Interpretation.SUSCEPTIBLE)
                .doesNotContainKey("MEM");
    }

    @Test
    void loMedidoNuncaSeSobrescribe() {
        Map<String, Interpretation> kpn = ExpectedPhenotypes.apply("kpn", Map.of("AMP", Interpretation.SUSCEPTIBLE));

        assertThat(kpn).containsEntry("AMP", Interpretation.SUSCEPTIBLE);
    }

    @Test
    void salmonellaEntericaNoEsSensibleEsperadaACarbapenemicos() {
        // EUCAST ESP v1.1, regla 1.2: solo Salmonella Typhi.
        assertThat(ExpectedPhenotypes.expected("sal", "MEM")).isEqualTo(ExpectedPhenotypes.Source.UNKNOWN);
        assertThat(ExpectedPhenotypes.expected("spn", "MEM")).isEqualTo(ExpectedPhenotypes.Source.EXPECTED_SUSCEPTIBLE);
    }
}
