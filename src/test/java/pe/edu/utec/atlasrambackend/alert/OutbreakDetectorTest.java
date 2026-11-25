package pe.edu.utec.atlasrambackend.alert;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class OutbreakDetectorTest {

    private final OutbreakDetector detector = new OutbreakDetector(0.0005, 4, 20);

    @Test
    void colaBinomialCoincideConElCalculoExacto() {
        // P(X >= 8) con X ~ Bin(10, 0.5) = (45 + 10 + 1) / 1024
        assertThat(OutbreakDetector.binomialUpperTail(10, 8, 0.5)).isCloseTo(56.0 / 1024, within(1e-12));
        assertThat(OutbreakDetector.binomialUpperTail(10, 0, 0.3)).isEqualTo(1.0);
        assertThat(OutbreakDetector.binomialUpperTail(10, 11, 0.3)).isEqualTo(0.0);
    }

    @Test
    void detectaElBroteDelSeed() {
        // HNHU, K. pneumoniae frente a meropenem: 9 de 13 resistentes en abril de 2026
        // frente a una línea base de alrededor de 8 de 64.
        var signal = detector.evaluate(13, 9, 64, 8);

        assertThat(signal).isPresent();
        assertThat(signal.get().pValue()).isLessThan(0.0005);
        assertThat(signal.get().observedRate()).isGreaterThan(signal.get().expectedRate());
    }

    @Test
    void noAlertaPorResistenciaCronicaAlta() {
        // A. baumannii frente a carbapenémicos: 80 % de resistencia siempre. No es un brote.
        assertThat(detector.evaluate(10, 8, 120, 96)).isEmpty();
    }

    @Test
    void exigeUnMinimoDeResistentes() {
        // 3 de 3 es llamativo, pero con tan pocos casos puede ser azar.
        assertThat(detector.evaluate(3, 3, 100, 5)).isEmpty();
    }

    @Test
    void exigeLineaBaseSuficiente() {
        // Sin historia (menos de 20 resultados previos) no hay contra qué comparar.
        assertThat(detector.evaluate(12, 10, 15, 0)).isEmpty();
    }

    @Test
    void lineaBaseEnCeroNoDisparaConPocaEvidencia() {
        // Con 0 de 40 previos, el suavizado evita que 4 de 40 resistentes se considere imposible.
        assertThat(detector.evaluate(40, 4, 40, 0)).isEmpty();
    }
}
