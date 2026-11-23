package pe.edu.utec.atlasrambackend.logistics;

import org.junit.jupiter.api.Test;
import pe.edu.utec.atlasrambackend.model.AwareCategory;
import pe.edu.utec.atlasrambackend.model.VenClass;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ReplenishmentPlannerTest {

    @Test
    void pedidoHastaSDescontandoLoQueYaViene() {
        int[] steady = new int[12];
        Arrays.fill(steady, 30);
        ReplenishmentPlanner.Plan withoutOrder = ReplenishmentPlanner.plan(
                new ReplenishmentPlanner.Input(steady, 20, 0, 30, 15, 0.95, 2031));
        ReplenishmentPlanner.Plan withOrder = ReplenishmentPlanner.plan(
                new ReplenishmentPlanner.Input(steady, 20, 10, 30, 15, 0.95, 2031));

        assertThat(withoutOrder.method()).isEqualTo(ForecastMethod.SES);
        assertThat(withoutOrder.protectionDays()).isEqualTo(45);
        assertThat(withoutOrder.orderUpTo()).isGreaterThanOrEqualTo((int) Math.ceil(withoutOrder.expectedProtectionDemand()));
        assertThat(withoutOrder.suggestedOrder()).isEqualTo(withoutOrder.orderUpTo() - 20);
        assertThat(withOrder.suggestedOrder()).isEqualTo(withoutOrder.suggestedOrder() - 10);
        assertThat(withoutOrder.coverageDays()).isCloseTo(20 / withoutOrder.dailyRate(), within(1e-9));
    }

    @Test
    void sinRotacionNoSugierePedido() {
        int[] stopped = {36, 41, 58, 39, 40, 0, 0, 0, 0, 0, 0, 0};
        ReplenishmentPlanner.Plan plan = ReplenishmentPlanner.plan(
                new ReplenishmentPlanner.Input(stopped, 46, 0, 30, 15, 0.95, 1));

        assertThat(plan.method()).isEqualTo(ForecastMethod.NONE);
        assertThat(plan.suggestedOrder()).isNull();
        assertThat(plan.coverageDays()).isNull();
    }

    @Test
    void historiaCortaSoloDaIndicadores() {
        ReplenishmentPlanner.Plan plan = ReplenishmentPlanner.plan(
                new ReplenishmentPlanner.Input(new int[]{5, 0}, 20, 3, 30, 15, 0.95, 1));
        assertThat(plan.method()).isEqualTo(ForecastMethod.NONE);
        assertThat(plan.inventoryPosition()).isEqualTo(23);
        assertThat(plan.orderUpTo()).isNull();
    }

    @Test
    void nivelDeServicioPorCriticidad() {
        assertThat(ServiceLevelPolicy.target(VenClass.NON_ESSENTIAL, AwareCategory.RESERVE, null)).isEqualTo(0.99);
        assertThat(ServiceLevelPolicy.target(VenClass.VITAL, AwareCategory.ACCESS, null)).isEqualTo(0.99);
        assertThat(ServiceLevelPolicy.target(VenClass.NON_ESSENTIAL, AwareCategory.WATCH, null)).isEqualTo(0.95);
        assertThat(ServiceLevelPolicy.target(VenClass.NON_ESSENTIAL, AwareCategory.ACCESS, null)).isEqualTo(0.90);
        assertThat(ServiceLevelPolicy.target(VenClass.VITAL, null, new BigDecimal("0.97"))).isEqualTo(0.97);
    }
}
