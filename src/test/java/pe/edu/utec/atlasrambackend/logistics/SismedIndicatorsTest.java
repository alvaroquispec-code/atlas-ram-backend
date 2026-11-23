package pe.edu.utec.atlasrambackend.logistics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SismedIndicatorsTest {

    private static final int[] SIX_MONTHS = {10, 0, 12, 8, 0, 10};

    @Test
    void elCpmaExcluyeLosMesesSinConsumo() {
        // 40 unidades en 4 meses con consumo: CPMA 10, no 40/6.
        SismedIndicators.Result r = SismedIndicators.compute(SIX_MONTHS, 30);

        assertThat(r.cpma()).isCloseTo(10.0, within(1e-9));
        assertThat(r.monthsOfStock()).isCloseTo(3.0, within(1e-9));
        assertThat(r.state()).isEqualTo(StockState.NORMAL_STOCK);
    }

    @Test
    void umbralesDeMesesDeExistencia() {
        assertThat(SismedIndicators.compute(SIX_MONTHS, 0).state()).isEqualTo(StockState.STOCKOUT);
        assertThat(SismedIndicators.compute(SIX_MONTHS, 15).state()).isEqualTo(StockState.UNDERSTOCK);
        assertThat(SismedIndicators.compute(SIX_MONTHS, 20).state()).isEqualTo(StockState.NORMAL_STOCK);
        assertThat(SismedIndicators.compute(SIX_MONTHS, 60).state()).isEqualTo(StockState.NORMAL_STOCK);
        assertThat(SismedIndicators.compute(SIX_MONTHS, 70).state()).isEqualTo(StockState.OVERSTOCK);
    }

    @Test
    void sinConsumoEnSeisMeses() {
        int[] none = {0, 0, 0, 0, 0, 0};
        assertThat(SismedIndicators.compute(none, 5).state()).isEqualTo(StockState.NO_ROTATION);
        assertThat(SismedIndicators.compute(none, 0).state()).isEqualTo(StockState.NO_MOVEMENT);
        assertThat(SismedIndicators.compute(none, 5).monthsOfStock()).isNull();
    }

    @Test
    void soloCuentanLosUltimosSeisMeses() {
        int[] twelve = {100, 100, 100, 100, 100, 100, 6, 6, 6, 6, 6, 6};
        assertThat(SismedIndicators.compute(twelve, 12).cpma()).isCloseTo(6.0, within(1e-9));
    }
}
