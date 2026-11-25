package pe.edu.utec.atlasrambackend.logistics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DemandClassifierTest {

    @Test
    void losCuatroCuadrantesSbc() {
        assertThat(DemandClassifier.classify(new int[]{10, 11, 9, 10, 12, 10, 9, 11, 10, 10, 11, 9}).pattern())
                .isEqualTo(DemandPattern.SMOOTH);
        assertThat(DemandClassifier.classify(new int[]{2, 30, 5, 40, 3, 25}).pattern())
                .isEqualTo(DemandPattern.ERRATIC);
        assertThat(DemandClassifier.classify(new int[]{0, 0, 5, 0, 0, 6, 0, 0, 5, 0, 0, 6}).pattern())
                .isEqualTo(DemandPattern.INTERMITTENT);
        assertThat(DemandClassifier.classify(new int[]{0, 0, 1, 0, 0, 30, 0, 0, 2, 0, 0, 25}).pattern())
                .isEqualTo(DemandPattern.LUMPY);
    }

    @Test
    void adiYCv2() {
        DemandClassifier.Result r = DemandClassifier.classify(new int[]{0, 0, 5, 0, 0, 6, 0, 0, 5, 0, 0, 6});
        assertThat(r.adi()).isCloseTo(3.0, within(1e-9));
        // tamaños 5, 6, 5, 6: media 5.5, varianza 0.25, CV² = 0.25 / 30.25
        assertThat(r.cv2()).isCloseTo(0.25 / 30.25, within(1e-9));
    }

    @Test
    void historiaInsuficiente() {
        assertThat(DemandClassifier.classify(new int[]{0, 0, 5, 0, 0}).pattern())
                .isEqualTo(DemandPattern.INSUFFICIENT_HISTORY);
        assertThat(DemandClassifier.classify(new int[]{0, 0, 5, 0, 0, 0, 0}).pattern())
                .isEqualTo(DemandPattern.INSUFFICIENT_HISTORY);
        assertThat(DemandClassifier.classify(new int[0]).pattern())
                .isEqualTo(DemandPattern.INSUFFICIENT_HISTORY);
    }
}
