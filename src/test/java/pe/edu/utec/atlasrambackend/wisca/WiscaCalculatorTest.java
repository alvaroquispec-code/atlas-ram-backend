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
    void unResistenteEsperadoDeEucastCuentaComoNoCubiertoYNoComoSinDatos() {
        List<WiscaCalculator.IsolateProfile> data = new ArrayList<>();
        data.addAll(isolates(1L, "eco", "Escherichia coli", 200, Map.of("CRO", SUSCEPTIBLE)));
        // A P. aeruginosa no se le prueba ceftriaxona: es un fenotipo resistente esperado (EUCAST ERP v1.2).
        data.addAll(isolates(3L, "pae", "Pseudomonas aeruginosa", 200, Map.of("MEM", SUSCEPTIBLE)));

        WiscaCalculator.RegimenResult cro = WiscaCalculator.compute(data, List.of(List.of("CRO")), 90, DRAWS, 1L)
                .regimens().get(0);

        assertThat(cro.median()).isCloseTo(50.0, within(2.0));
        assertThat(cro.expectedResistantPathogens()).containsExactly("Pseudomonas aeruginosa");
        assertThat(cro.untestedPathogens()).isEmpty();
        assertThat(cro.untestedFrequency()).isZero();
    }

    @Test
    void unSensibleEsperadoDeEucastCuentaComoCubierto() {
        List<WiscaCalculator.IsolateProfile> data = new ArrayList<>();
        data.addAll(isolates(1L, "eco", "Escherichia coli", 200, Map.of("MEM", SUSCEPTIBLE)));
        // A S. pneumoniae no se le prueba meropenem: es un fenotipo sensible esperado (EUCAST ESP v1.1, regla 2.4).
        data.addAll(isolates(7L, "spn", "Streptococcus pneumoniae", 200, Map.of("CRO", SUSCEPTIBLE)));

        WiscaCalculator.RegimenResult mem = WiscaCalculator.compute(data, List.of(List.of("MEM")), 90, DRAWS, 1L)
                .regimens().get(0);

        assertThat(mem.median()).isGreaterThan(98.0);
        assertThat(mem.expectedSusceptiblePathogens()).containsExactly("Streptococcus pneumoniae");
        assertThat(mem.untestedPathogens()).isEmpty();
    }

    @Test
    void unGermenSinPruebasNiFenotipoEsperadoQuedaSinDatosYCuentaComoNoCubierto() {
        List<WiscaCalculator.IsolateProfile> data = new ArrayList<>();
        data.addAll(isolates(1L, "eco", "Escherichia coli", 200, Map.of("MEM", SUSCEPTIBLE)));
        // Salmonella enterica frente a meropenem: EUCAST solo da sensible esperado a Salmonella Typhi.
        data.addAll(isolates(8L, "sal", "Salmonella enterica", 200, Map.of("CIP", SUSCEPTIBLE)));

        WiscaCalculator.RegimenResult mem = WiscaCalculator.compute(data, List.of(List.of("MEM")), 90, DRAWS, 1L)
                .regimens().get(0);

        assertThat(mem.median()).isCloseTo(50.0, within(2.0));
        assertThat(mem.untestedPathogens()).containsExactly("Salmonella enterica");
        assertThat(mem.untestedFrequency()).isCloseTo(50.0, within(0.5));
    }

    @Test
    void unResultadoMedidoNoSeSobrescribeConElFenotipoEsperado() {
        // Un S. pneumoniae informado resistente a meropenem es inusual, pero manda lo medido.
        List<WiscaCalculator.IsolateProfile> data =
                new ArrayList<>(isolates(7L, "spn", "Streptococcus pneumoniae", 100, Map.of("MEM", RESISTANT)));

        WiscaCalculator.RegimenResult mem = WiscaCalculator.compute(data, List.of(List.of("MEM")), 90, DRAWS, 1L)
                .regimens().get(0);

        assertThat(mem.median()).isLessThan(5.0);
        assertThat(mem.expectedSusceptiblePathogens()).isEmpty();
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
