package pe.edu.utec.atlasrambackend.wisca;

import org.junit.jupiter.api.Test;
import pe.edu.utec.atlasrambackend.model.Interpretation;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExpertRulesTest {

    @Test
    void sarmEsResistenteATodosLosBetalactamicos() {
        Map<String, Interpretation> r = ExpertRules.apply("sau", Map.of("OXA", Interpretation.RESISTANT));
        assertThat(r).containsEntry("MEM", Interpretation.RESISTANT).containsEntry("CZO", Interpretation.RESISTANT);
    }

    @Test
    void sasmEsSensibleALosBetalactamicosAntiestafilococicosPeroNoALaAmpicilina() {
        Map<String, Interpretation> r = ExpertRules.apply("sau", Map.of("OXA", Interpretation.SUSCEPTIBLE));
        assertThat(r).containsEntry("CZO", Interpretation.SUSCEPTIBLE).doesNotContainKey("AMP").doesNotContainKey("CAZ");
    }

    @Test
    void unResultadoMedidoNuncaSeSobrescribe() {
        Map<String, Interpretation> r = ExpertRules.apply("sau",
                Map.of("OXA", Interpretation.SUSCEPTIBLE, "CZO", Interpretation.RESISTANT));
        assertThat(r).containsEntry("CZO", Interpretation.RESISTANT);
    }

    @Test
    void soloAplicaAStaphylococcusAureus() {
        Map<String, Interpretation> measured = Map.of("OXA", Interpretation.RESISTANT);
        assertThat(ExpertRules.apply("eco", measured)).isSameAs(measured);
    }
}
