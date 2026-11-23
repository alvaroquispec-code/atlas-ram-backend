package pe.edu.utec.atlasrambackend.logistics;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class DemandBootstrapTest {

    private static final double HORIZON = 45 / ReplenishmentPlanner.DAYS_PER_MONTH;

    @Test
    void laMediaRespetaElHorizonte() {
        int[] steady = new int[12];
        Arrays.fill(steady, 30);
        DemandBootstrap.Result r = DemandBootstrap.simulate(steady, HORIZON, 0.95, 5_000, 2031);
        // 30 por mes durante 1.48 meses ≈ 44.4 (el jittering añade ≈ 0.5 por mes)
        assertThat(r.mean()).isCloseTo(30 * HORIZON, within(2.0));
        assertThat(r.quantile()).isGreaterThan(r.mean());
    }

    @Test
    void mayorNivelDeServicioPideMas() {
        int[] intermittent = {0, 0, 5, 0, 0, 5, 0, 0, 5, 0, 0, 5};
        double p95 = DemandBootstrap.simulate(intermittent, HORIZON, 0.95, 5_000, 2031).quantile();
        double p99 = DemandBootstrap.simulate(intermittent, HORIZON, 0.99, 5_000, 2031).quantile();
        assertThat(p99).isGreaterThanOrEqualTo(p95);
    }

    @Test
    void esReproducibleConLaMismaSemilla() {
        int[] series = {3, 0, 8, 0, 0, 12, 4, 0, 0, 9};
        assertThat(DemandBootstrap.simulate(series, HORIZON, 0.95, 2_000, 7))
                .isEqualTo(DemandBootstrap.simulate(series, HORIZON, 0.95, 2_000, 7));
    }

    @Test
    void necesitaDosMesesConDemanda() {
        assertThatThrownBy(() -> DemandBootstrap.simulate(new int[]{0, 0, 4, 0}, HORIZON, 0.95, 100, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
