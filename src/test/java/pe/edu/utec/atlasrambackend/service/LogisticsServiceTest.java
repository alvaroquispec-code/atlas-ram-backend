package pe.edu.utec.atlasrambackend.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LogisticsServiceTest {

    private static final LocalDate WINDOW_START = LocalDate.of(2025, 11, 1);
    private static final LocalDate CURRENT_MONTH = LocalDate.of(2026, 11, 1);
    private static final int[] FULL = {0, 0, 0, 0, 0, 0, 0, 0, 10, 12, 9, 11};

    @Test
    void laSerieEmpiezaEnElPrimerMesConMovimiento() {
        // el ítem existe desde julio de 2026: los 8 meses previos no son "meses sin demanda"
        int[] series = LogisticsService.activeSeries(FULL, LocalDate.of(2026, 7, 15), WINDOW_START, CURRENT_MONTH);
        assertThat(series).containsExactly(10, 12, 9, 11);
    }

    @Test
    void unItemAntiguoUsaLosDoceMeses() {
        int[] series = LogisticsService.activeSeries(FULL, LocalDate.of(2024, 1, 1), WINDOW_START, CURRENT_MONTH);
        assertThat(series).hasSize(LogisticsService.HISTORY_MONTHS);
    }

    @Test
    void sinMovimientosOSoloEnElMesEnCursoNoHaySerie() {
        assertThat(LogisticsService.activeSeries(null, null, WINDOW_START, CURRENT_MONTH)).isEmpty();
        assertThat(LogisticsService.activeSeries(null, LocalDate.of(2026, 11, 3), WINDOW_START, CURRENT_MONTH)).isEmpty();
        // recibido en octubre, sin consumos: un mes en cero
        assertThat(LogisticsService.activeSeries(null, LocalDate.of(2026, 10, 3), WINDOW_START, CURRENT_MONTH))
                .containsExactly(0);
    }
}
