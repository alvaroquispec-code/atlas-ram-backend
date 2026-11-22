package pe.edu.utec.atlasrambackend.wisca;

import org.junit.jupiter.api.Test;
import pe.edu.utec.atlasrambackend.model.Interpretation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static pe.edu.utec.atlasrambackend.model.Interpretation.RESISTANT;
import static pe.edu.utec.atlasrambackend.model.Interpretation.SUSCEPTIBLE;

class WiscaCalculatorTest {

    private static final int DRAWS = 4000;

    private static List<WiscaCalculator.IsolateProfile> isolates(long id, String code, String name, int count,
                                                                 Map<String, Interpretation> profile) {
        List<WiscaCalculator.IsolateProfile> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new WiscaCalculator.IsolateProfile(id, code, name, profile));
        }
        return list;
    }

    @Test
    void laCombinacionSeEvaluaEnElMismoAislamiento() {
        Map<String, Interpretation> resistantToTzp = Map.of("TZP", RESISTANT, "AMK", SUSCEPTIBLE);
        assertThat(WiscaCalculator.coverage(resistantToTzp, List.of("TZP", "AMK"))).isTrue();
        assertThat(WiscaCalculator.coverage(Map.of("TZP", RESISTANT, "AMK", RESISTANT), List.of("TZP", "AMK"))).isFalse();
        // Falta AMK y TZP es R: no se sabe → no evaluable.
        assertThat(WiscaCalculator.coverage(Map.of("TZP", RESISTANT), List.of("TZP", "AMK"))).isNull();
        // Falta AMK pero TZP ya es S: cubierto igual.
        assertThat(WiscaCalculator.coverage(Map.of("TZP", SUSCEPTIBLE), List.of("TZP", "AMK"))).isTrue();
    }

    @Test
    void laCoberturaPonderaPorLaFrecuenciaDeCadaGermen() {
        // 300 E. coli siempre sensibles a MEM y 100 P. aeruginosa siempre resistentes:
        // la cobertura de MEM debe rondar el 75 % (la frecuencia de E. coli).
        List<WiscaCalculator.IsolateProfile> data = new ArrayList<>();
        data.addAll(isolates(1L, "eco", "Escherichia coli", 300, Map.of("MEM", SUSCEPTIBLE)));
        data.addAll(isolates(3L, "pae", "Pseudomonas aeruginosa", 100, Map.of("MEM", RESISTANT)));

        WiscaCalculator.Result r = WiscaCalculator.compute(data, List.of(List.of("MEM")), 90, DRAWS, 1L);

        WiscaCalculator.RegimenResult mem = r.regimens().get(0);
        assertThat(mem.median()).isCloseTo(75.0, within(1.5));
        assertThat(mem.low()).isLessThan(mem.median());
        assertThat(mem.high()).isGreaterThan(mem.median());
        assertThat(mem.probabilityAboveTarget()).isZero();
        assertThat(r.pathogens().get(0).microorganismName()).isEqualTo("Escherichia coli");
    }

    @Test
    void unGermenSinPruebasCuentaComoNoCubiertoYSeInforma() {
        List<WiscaCalculator.IsolateProfile> data = new ArrayList<>();
        data.addAll(isolates(1L, "eco", "Escherichia coli", 200, Map.of("CRO", SUSCEPTIBLE)));
        // A P. aeruginosa no se le prueba ceftriaxona (resistencia intrínseca).
        data.addAll(isolates(3L, "pae", "Pseudomonas aeruginosa", 200, Map.of("MEM", SUSCEPTIBLE)));

        WiscaCalculator.RegimenResult cro = WiscaCalculator.compute(data, List.of(List.of("CRO")), 90, DRAWS, 1L)
                .regimens().get(0);

        assertThat(cro.median()).isCloseTo(50.0, within(2.0));
        assertThat(cro.untestedPathogens()).containsExactly("Pseudomonas aeruginosa");
        assertThat(cro.untestedFrequency()).isCloseTo(50.0, within(0.5));
    }

    @Test
    void elMejorRegimenYLaProbabilidadDeSuperarElObjetivo() {
        List<WiscaCalculator.IsolateProfile> data = new ArrayList<>();
        data.addAll(isolates(1L, "eco", "Escherichia coli", 380, Map.of("MEM", SUSCEPTIBLE, "CIP", RESISTANT)));
        data.addAll(isolates(1L, "eco", "Escherichia coli", 20, Map.of("MEM", SUSCEPTIBLE, "CIP", SUSCEPTIBLE)));

        WiscaCalculator.Result r = WiscaCalculator.compute(data,
                List.of(List.of("CIP"), List.of("MEM")), 90, DRAWS, 1L);

        assertThat(r.regimens().get(0).antibiotics()).containsExactly("MEM");   // ordenado por mediana
        assertThat(r.regimens().get(0).probabilityBest()).isEqualTo(1.0);
        assertThat(r.regimens().get(0).probabilityAboveTarget()).isGreaterThan(0.99);
        assertThat(r.regimens().get(1).probabilityAboveTarget()).isZero();
    }

    @Test
    void mismaSemillaMismoResultado() {
        List<WiscaCalculator.IsolateProfile> data =
                isolates(1L, "eco", "Escherichia coli", 50, Map.of("MEM", SUSCEPTIBLE, "CRO", RESISTANT));
        var a = WiscaCalculator.compute(data, List.of(List.of("MEM")), 90, 1000, 7L);
        var b = WiscaCalculator.compute(data, List.of(List.of("MEM")), 90, 1000, 7L);
        assertThat(a.regimens().get(0).median()).isEqualTo(b.regimens().get(0).median());
    }

    @Test
    void sinAislamientosNoHayResultado() {
        assertThat(WiscaCalculator.compute(List.of(), List.of(List.of("MEM")), 90, 100, 1L).regimens()).isEmpty();
    }

    @Test
    void cuantilPorInterpolacion() {
        assertThat(WiscaCalculator.quantile(new double[]{0, 10, 20, 30, 40}, 0.5)).isEqualTo(20);
        assertThat(WiscaCalculator.quantile(new double[]{0, 10}, 0.25)).isEqualTo(2.5);
    }

    @Test
    void unFarmacoQueNuncaSePruebaFrenteAlGermenNoLoCubre() {
        // A E. coli nunca se le prueba vancomicina. Los resistentes a meropenem no deben quedar
        // "no evaluables" en MEM + VAN (eso los sacaría del cálculo y daría 100 %): no están cubiertos.
        List<WiscaCalculator.IsolateProfile> data = new ArrayList<>();
        data.addAll(isolates(1L, "eco", "Escherichia coli", 90, Map.of("MEM", SUSCEPTIBLE)));
        data.addAll(isolates(1L, "eco", "Escherichia coli", 10, Map.of("MEM", RESISTANT)));

        WiscaCalculator.RegimenResult memVan = WiscaCalculator.compute(data,
                List.of(List.of("MEM", "VAN")), 90, 1000, 1L).regimens().get(0);

        assertThat(memVan.byPathogen().get(0).evaluated()).isEqualTo(100);
        assertThat(memVan.byPathogen().get(0).covered()).isEqualTo(90);
    }
}
