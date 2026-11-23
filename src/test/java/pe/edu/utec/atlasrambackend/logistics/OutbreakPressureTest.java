package pe.edu.utec.atlasrambackend.logistics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutbreakPressureTest {

    private static final List<OutbreakPressure.ActiveAlert> ESBL_ALERT = List.of(
            new OutbreakPressure.ActiveAlert("Klebsiella pneumoniae", "Cefalosporina de 3.ª generación"));

    @Test
    void escalamientoConPocaCoberturaDuranteUnBrote() {
        // R + L = 45 días; se exige 1.5 × 45 = 67.5 días
        assertThat(OutbreakPressure.evaluate(ESBL_ALERT, "MEM", "Carbapenémico", 100, 20.0, 45)).isPresent();
        assertThat(OutbreakPressure.evaluate(ESBL_ALERT, "MEM", "Carbapenémico", 100, 80.0, 45)).isEmpty();
    }

    @Test
    void noSeMarcaElFarmacoDeLaMismaClaseNiLoQueNoEsEscalamiento() {
        List<OutbreakPressure.ActiveAlert> creAlert = List.of(
                new OutbreakPressure.ActiveAlert("Klebsiella pneumoniae", "Carbapenémico"));
        assertThat(OutbreakPressure.evaluate(creAlert, "MEM", "Carbapenémico", 100, 20.0, 45)).isEmpty();
        assertThat(OutbreakPressure.evaluate(ESBL_ALERT, "ERY", "Macrólido", 0, null, 45)).isEmpty();
    }

    @Test
    void sinAlertasNoHayPresion() {
        assertThat(OutbreakPressure.evaluate(List.of(), "MEM", "Carbapenémico", 0, 0.0, 45)).isEmpty();
    }

    @Test
    void sinStockDuranteUnBroteSeMarca() {
        assertThat(OutbreakPressure.evaluate(ESBL_ALERT, "AMK", "Aminoglucósido", 0, null, 45)).isPresent();
    }
}
