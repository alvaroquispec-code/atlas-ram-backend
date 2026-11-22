package pe.edu.utec.atlasrambackend.stats;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ProportionsTest {

    private static final double EPS = 0.01;   // centésima de punto porcentual

    @Test
    void valoresDeReferencia() {
        // Calculados con la fórmula de Wilson (z = 1,96). 5 de 10: el intervalo es simétrico alrededor de 50 %.
        Proportions.Interval half = Proportions.wilson(5, 10);
        assertThat(half.low()).isCloseTo(23.66, within(EPS));
        assertThat(half.high()).isCloseTo(76.34, within(EPS));

        Proportions.Interval almostAll = Proportions.wilson(29, 30);
        assertThat(almostAll.low()).isCloseTo(83.33, within(EPS));
        assertThat(almostAll.high()).isCloseTo(99.41, within(EPS));
    }

    @Test
    void ceroDeDiezNoEsCeroPorCiento() {
        // 0 resistentes en 10 no prueba que la resistencia sea 0 %: puede llegar a 27,8 %.
        // El intervalo de Wald daría [0, 0], que es engañoso.
        Proportions.Interval none = Proportions.wilson(0, 10);
        assertThat(none.low()).isZero();
        assertThat(none.high()).isCloseTo(27.75, within(EPS));
    }

    @Test
    void nuncaSaleDelRangoCeroCien() {
        // Con Wald, 1 de 20 da un límite inferior negativo (5 % − 9,6 %).
        Proportions.Interval rare = Proportions.wilson(1, 20);
        assertThat(rare.low()).isCloseTo(0.89, within(EPS)).isPositive();
        assertThat(Proportions.wilson(20, 20).high()).isEqualTo(100.0);
    }

    @Test
    void sinDatosNoHayInformacion() {
        Proportions.Interval empty = Proportions.wilson(0, 0);
        assertThat(empty.low()).isZero();
        assertThat(empty.high()).isEqualTo(100.0);
    }

    @Test
    void masDatosIntervaloMasEstrecho() {
        Proportions.Interval small = Proportions.wilson(10, 30);
        Proportions.Interval large = Proportions.wilson(1000, 3000);
        assertThat(large.high() - large.low()).isLessThan((small.high() - small.low()) / 5);
    }

    @Test
    void argumentosImposibles() {
        assertThatThrownBy(() -> Proportions.wilson(11, 10)).isInstanceOf(IllegalArgumentException.class);
    }
}
