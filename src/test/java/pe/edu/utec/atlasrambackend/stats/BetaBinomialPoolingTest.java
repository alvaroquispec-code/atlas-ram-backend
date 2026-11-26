package pe.edu.utec.atlasrambackend.stats;

import org.junit.jupiter.api.Test;
import pe.edu.utec.atlasrambackend.stats.BetaBinomialPooling.Group;
import pe.edu.utec.atlasrambackend.stats.BetaBinomialPooling.Prior;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BetaBinomialPoolingTest {

    private static final List<Group> RED = List.of(new Group(4, 8), new Group(10, 100), new Group(15, 120),
            new Group(30, 200), new Group(5, 60));

    @Test
    void cuantilesDeLaBetaCoincidenConValoresDeReferencia() {
        // Beta(2, 1): F(x) = x², cuantiles exactos √0,025 y √0,975 (también los da scipy.stats.beta.ppf).
        assertThat(BetaBinomialPooling.betaQuantile(0.025, 2, 1)).isCloseTo(Math.sqrt(0.025), within(1e-9));
        assertThat(BetaBinomialPooling.betaQuantile(0.975, 2, 1)).isCloseTo(Math.sqrt(0.975), within(1e-9));
        // Beta(1, 1) es la uniforme.
        assertThat(BetaBinomialPooling.regularizedBeta(0.3, 1, 1)).isCloseTo(0.3, within(1e-12));
    }

    @Test
    void conPocosDatosLaEstimacionSeAcercaALaMediaDeLaRed() {
        Prior prior = BetaBinomialPooling.fit(RED);
        assertThat(prior).isNotNull();
        assertThat(prior.mean()).isCloseTo(64.0 / 488, within(1e-12));

        // 4 de 8 (50 %) baja hacia la media de la red (≈13 %); valores verificados con scipy.stats.beta.
        var pequeno = BetaBinomialPooling.estimate(prior, 4, 8);
        assertThat(pequeno.percentage()).isCloseTo(19.18, within(0.01));
        assertThat(pequeno.low()).isCloseTo(9.52, within(0.01));
        assertThat(pequeno.high()).isCloseTo(31.23, within(0.01));

        // Con 200 aislamientos el dato local casi no se mueve (15 % → 14,7 %).
        var grande = BetaBinomialPooling.estimate(prior, 30, 200);
        assertThat(grande.percentage()).isCloseTo(14.68, within(0.01));
    }

    @Test
    void sinGruposSuficientesNoSeEstima() {
        assertThat(BetaBinomialPooling.fit(List.of(new Group(1, 10), new Group(2, 10)))).isNull();
        assertThat(BetaBinomialPooling.fit(List.of(new Group(0, 10), new Group(0, 20), new Group(0, 5)))).isNull();
        assertThat(BetaBinomialPooling.estimate(BetaBinomialPooling.fit(RED), 0, 0)).isNull();
    }
}
