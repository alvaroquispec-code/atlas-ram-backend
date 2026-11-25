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

    @Test
    void stockDeSeguridadNuncaMenorAl10PorCiento() {
        // Demanda casi constante: el bootstrap pediría muy poco colchón; el piso lo sube a 10 %.
        int[] steady = {300, 302, 298, 301, 299, 300, 300, 301, 299, 300, 302, 298};
        ReplenishmentPlanner.Plan plan = ReplenishmentPlanner.plan(
                new ReplenishmentPlanner.Input(steady, 0, 0, 30, 15, 0.90, 7));

        double expected = plan.expectedProtectionDemand();
        assertThat(plan.orderUpTo()).isGreaterThanOrEqualTo((int) Math.ceil(expected * 1.10));
        assertThat(plan.safetyStock()).isGreaterThanOrEqualTo((int) Math.floor(expected * 0.10));
        assertThat(plan.safetyFloorApplied()).isTrue();
    }

    @Test
    void conDemandaVariableMandaElBootstrap() {
        int[] volatileSeries = {10, 90, 0, 120, 5, 80, 0, 150, 20, 60, 0, 110};
        ReplenishmentPlanner.Plan plan = ReplenishmentPlanner.plan(
                new ReplenishmentPlanner.Input(volatileSeries, 0, 0, 30, 15, 0.99, 7));

        assertThat(plan.safetyFloorApplied()).isFalse();
        assertThat(plan.safetyStock()).isGreaterThan((int) Math.ceil(plan.expectedProtectionDemand() * 0.10));
    }
}
