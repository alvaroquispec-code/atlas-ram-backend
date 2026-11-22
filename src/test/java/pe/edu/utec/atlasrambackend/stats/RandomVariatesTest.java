package pe.edu.utec.atlasrambackend.stats;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RandomVariatesTest {

    @Test
    void lasMediasCoincidenConLaTeoria() {
        RandomVariates rv = new RandomVariates(42);
        int n = 50_000;
        double gamma = 0, gammaSmall = 0, beta = 0;
        for (int i = 0; i < n; i++) {
            gamma += rv.gamma(3.5);        // media 3,5
            gammaSmall += rv.gamma(0.4);   // media 0,4 (rama forma < 1)
            beta += rv.beta(3, 7);         // media 3/(3+7) = 0,3
        }
        assertThat(gamma / n).isCloseTo(3.5, within(0.05));
        assertThat(gammaSmall / n).isCloseTo(0.4, within(0.02));
        assertThat(beta / n).isCloseTo(0.3, within(0.005));
    }

    @Test
    void dirichletSumaUno() {
        double[] d = new RandomVariates(1).dirichlet(new double[]{2, 5, 1});
        assertThat(d[0] + d[1] + d[2]).isCloseTo(1.0, within(1e-12));
    }
}
