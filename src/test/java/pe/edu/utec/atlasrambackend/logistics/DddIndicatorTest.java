package pe.edu.utec.atlasrambackend.logistics;

import org.junit.jupiter.api.Test;
import pe.edu.utec.atlasrambackend.model.AwareCategory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DddIndicatorTest {

    @Test
    void dddPor100CamasDiaYPorGrupoAware() {
        // meropenem 300 g ÷ 3 g = 100 DDD; amikacina 50 g ÷ 1 g = 50 DDD; vancomicina sin DDD: se excluye
        DddIndicator.Result r = DddIndicator.compute(List.of(
                new DddIndicator.Consumption("MEM", AwareCategory.WATCH, 300, 3.0),
                new DddIndicator.Consumption("AMK", AwareCategory.ACCESS, 50, 1.0),
                new DddIndicator.Consumption("VAN", AwareCategory.WATCH, 100, null)), 1000.0);

        assertThat(r.totalDdd()).isCloseTo(150, within(1e-9));
        assertThat(r.dddPer100BedDays()).isCloseTo(15.0, within(1e-9));
        assertThat(r.accessShare()).isCloseTo(1 / 3.0, within(1e-9));
        assertThat(r.withoutDdd()).containsExactly("VAN");
    }

    @Test
    void sinDiasCamaNoHayIndicadorPeroSiTotales() {
        DddIndicator.Result r = DddIndicator.compute(List.of(
                new DddIndicator.Consumption("MEM", AwareCategory.WATCH, 30, 3.0)), null);
        assertThat(r.totalDdd()).isCloseTo(10, within(1e-9));
        assertThat(r.dddPer100BedDays()).isNull();
    }
}
