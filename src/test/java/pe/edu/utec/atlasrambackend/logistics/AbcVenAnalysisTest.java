package pe.edu.utec.atlasrambackend.logistics;

import org.junit.jupiter.api.Test;
import pe.edu.utec.atlasrambackend.model.VenClass;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AbcVenAnalysisTest {

    @Test
    void abcPorValorAcumulado() {
        Map<Long, AbcClass> abc = AbcVenAnalysis.classify(Map.of(1L, 800.0, 2L, 150.0, 3L, 40.0, 4L, 10.0, 5L, 0.0));

        assertThat(abc.get(1L)).isEqualTo(AbcClass.A);   // acumulado previo 0 %
        assertThat(abc.get(2L)).isEqualTo(AbcClass.B);   // previo 80 %
        assertThat(abc.get(3L)).isEqualTo(AbcClass.C);   // previo 95 %
        assertThat(abc.get(4L)).isEqualTo(AbcClass.C);
        assertThat(abc.get(5L)).isEqualTo(AbcClass.C);
    }

    @Test
    void matrizAbcVen() {
        assertThat(AbcVenAnalysis.category(AbcClass.A, VenClass.NON_ESSENTIAL)).isEqualTo(1);
        assertThat(AbcVenAnalysis.category(AbcClass.C, VenClass.VITAL)).isEqualTo(1);
        assertThat(AbcVenAnalysis.category(AbcClass.B, VenClass.ESSENTIAL)).isEqualTo(2);
        assertThat(AbcVenAnalysis.category(AbcClass.C, VenClass.NON_ESSENTIAL)).isEqualTo(3);
        assertThat(AbcVenAnalysis.category(null, VenClass.VITAL)).isNull();
    }
}
