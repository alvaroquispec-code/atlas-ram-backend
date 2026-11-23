package pe.edu.utec.atlasrambackend.logistics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ForecastingTest {

    @Test
    void sesDeUnaSerieConstanteEsLaConstante() {
        assertThat(Forecasting.ses(new int[]{10, 10, 10, 10}, 0.1)).isCloseTo(10.0, within(1e-9));
    }

    @Test
    void sbaCorrigeElSesgoDeCroston() {
        // tamaño 5 cada 3 meses: Croston = 5/3; SBA = (1 − 0.1/2) · 5/3
        int[] series = {0, 0, 5, 0, 0, 5, 0, 0, 5, 0, 0, 5};
        assertThat(Forecasting.sba(series, 0.1)).isCloseTo(0.95 * 5 / 3.0, within(1e-9));
    }

    @Test
    void tsbBajaCuandoLaDemandaSeApaga() {
        int[] dying = {0, 5, 0, 5, 0, 5, 0, 5, 0, 0, 0, 0};
        assertThat(Forecasting.choose(DemandPattern.INTERMITTENT, dying)).isEqualTo(ForecastMethod.TSB);
        assertThat(Forecasting.tsb(dying, 0.1, 0.1)).isLessThan(Forecasting.sba(dying, 0.1));
    }

    @Test
    void eleccionDelMetodo() {
        int[] any = {1, 2, 3};
        assertThat(Forecasting.choose(DemandPattern.SMOOTH, any)).isEqualTo(ForecastMethod.SES);
        assertThat(Forecasting.choose(DemandPattern.ERRATIC, any)).isEqualTo(ForecastMethod.SES);
        assertThat(Forecasting.choose(DemandPattern.LUMPY, any)).isEqualTo(ForecastMethod.SBA);
        assertThat(Forecasting.choose(DemandPattern.INSUFFICIENT_HISTORY, any)).isEqualTo(ForecastMethod.NONE);
    }
}
