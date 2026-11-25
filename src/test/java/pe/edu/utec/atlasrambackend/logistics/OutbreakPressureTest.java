package pe.edu.utec.atlasrambackend.logistics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OutbreakPressureTest {

    /** K. pneumoniae resistente a carbapenémicos; según el antibiograma local solo la amikacina sigue activa. */
    private static final List<OutbreakPressure.ActiveAlert> CRE_ALERT = List.of(
            new OutbreakPressure.ActiveAlert("Klebsiella pneumoniae", "Carbapenémico", Set.of("AMK")));

    @Test
    void elFarmacoActivoConPocaCoberturaSeMarca() {
        // R + L = 45 días; se exige 1.5 × 45 = 67.5 días
        assertThat(OutbreakPressure.evaluate(CRE_ALERT, "AMK", "Aminoglucósido", 100, 20.0, 45)).isPresent();
        assertThat(OutbreakPressure.evaluate(CRE_ALERT, "AMK", "Aminoglucósido", 100, 80.0, 45)).isEmpty();
    }

    @Test
    void noSeMarcanLosQueYaNoSirvenNiLosQueNoSePruebanContraEseGermen() {
        // meropenem es de la misma clase que la alerta; vancomicina no se prueba contra Klebsiella
        assertThat(OutbreakPressure.evaluate(CRE_ALERT, "MEM", "Carbapenémico", 0, null, 45)).isEmpty();
        assertThat(OutbreakPressure.evaluate(CRE_ALERT, "VAN", "Glucopéptido", 0, null, 45)).isEmpty();
    }

    @Test
    void sinAlertasNoHayPresion() {
        assertThat(OutbreakPressure.evaluate(List.of(), "AMK", "Aminoglucósido", 0, 0.0, 45)).isEmpty();
    }

    @Test
    void sinStockDuranteUnBroteSeMarca() {
        assertThat(OutbreakPressure.evaluate(CRE_ALERT, "AMK", "Aminoglucósido", 0, null, 45)).isPresent();
    }

    @Test
    void unFarmacoEsActivoConAlMenos80PorCientoYTreintaResultados() {
        assertThat(OutbreakPressure.activeDrug(92, 75)).isTrue();    // 81.5 %
        assertThat(OutbreakPressure.activeDrug(98, 59)).isFalse();   // 60.2 %
        assertThat(OutbreakPressure.activeDrug(20, 20)).isFalse();   // 100 % pero n < 30
    }
}
