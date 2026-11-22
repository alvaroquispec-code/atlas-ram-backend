package pe.edu.utec.atlasrambackend.stats;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CochranArmitageTest {

    private static CochranArmitage.Point p(int t, long x, long n) {
        return new CochranArmitage.Point(t, x, n);
    }

    @Test
    void resistenciaQueSubeConClaridad() {
        // 10 %, 20 %, 40 %, 60 % en cuatro periodos de 20 aislamientos. Valores calculados a mano.
        CochranArmitage.Result r = CochranArmitage.test(List.of(p(0, 2, 20), p(1, 4, 20), p(2, 8, 20), p(3, 12, 20)));

        assertThat(r.z()).isCloseTo(3.6296, within(1e-3));
        assertThat(r.pValue()).isCloseTo(0.000284, within(1e-5));
        assertThat(r.slope()).isCloseTo(0.17, within(1e-9));   // +17 puntos por periodo
        assertThat(r.periods()).isEqualTo(4);
    }

    @Test
    void subidasYBajadasSinDireccionNoSonTendencia() {
        CochranArmitage.Result r = CochranArmitage.test(List.of(p(0, 5, 50), p(1, 6, 50), p(2, 4, 50), p(3, 5, 50)));

        assertThat(r.z()).isCloseTo(-0.2108, within(1e-3));
        assertThat(r.pValue()).isCloseTo(0.833, within(1e-3));
    }

    @Test
    void losPeriodosVaciosNoCuentan() {
        CochranArmitage.Result r = CochranArmitage.test(List.of(p(0, 2, 20), p(1, 0, 0), p(2, 8, 20)));
        assertThat(r.periods()).isEqualTo(2);
    }

    @Test
    void sinVariacionNoHayPrueba() {
        assertThat(CochranArmitage.test(List.of(p(0, 0, 30), p(1, 0, 25)))).isNull();     // siempre 0 %
        assertThat(CochranArmitage.test(List.of(p(0, 3, 30)))).isNull();                 // un solo periodo
    }

    @Test
    void normalEstandar() {
        assertThat(Normal.cdf(0)).isCloseTo(0.5, within(1e-7));
        assertThat(Normal.cdf(1.959963984540054)).isCloseTo(0.975, within(1e-7));
        assertThat(Normal.cdf(-1)).isCloseTo(0.158655, within(1e-6));
        assertThat(Normal.cdf(3)).isCloseTo(0.998650, within(1e-6));
    }
}
