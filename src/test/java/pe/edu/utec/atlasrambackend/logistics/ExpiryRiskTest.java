package pe.edu.utec.atlasrambackend.logistics;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExpiryRiskTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 22);

    @Test
    void fefoPrimeroElLoteQueVenceAntes() {
        // 2 unidades/día. El lote 1 vence en 30 días: se usan 60 y quedan 40 en riesgo.
        // El lote 2 vence en 90 días: hasta entonces se piden 180, 60 ya los dio el lote 1; alcanza para sus 100.
        List<ExpiryRisk.LotRisk> risk = ExpiryRisk.evaluate(List.of(
                new ExpiryRisk.Lot(2L, TODAY.plusDays(90), 100),
                new ExpiryRisk.Lot(1L, TODAY.plusDays(30), 100)), 2.0, TODAY);

        assertThat(risk).extracting(ExpiryRisk.LotRisk::lotId).containsExactly(1L, 2L);
        assertThat(risk).extracting(ExpiryRisk.LotRisk::unitsAtRisk).containsExactly(40, 0);
    }

    @Test
    void sinConsumoTodoElLoteEstaEnRiesgo() {
        assertThat(ExpiryRisk.evaluate(List.of(new ExpiryRisk.Lot(1L, TODAY.plusDays(300), 46)), 0, TODAY))
                .extracting(ExpiryRisk.LotRisk::unitsAtRisk).containsExactly(46);
    }
}
