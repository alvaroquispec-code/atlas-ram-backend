package pe.edu.utec.atlasrambackend.service;

import org.junit.jupiter.api.Test;
import pe.edu.utec.atlasrambackend.dto.AntibiogramResponseDTO;
import pe.edu.utec.atlasrambackend.repository.AntibiogramQueries;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AntibiogramServiceTest {

    /** E. coli · meropenem: 40 primeros aislamientos (38 S, 1 I, 1 R), 10 repetidos excluidos, 30 identificados. */
    private static final AntibiogramQueries.Row MEROPENEM =
            new AntibiogramQueries.Row(1L, "Escherichia coli", 11L, "Meropenem", 40, 38, 1, 1, 30, 50);

    /** Solo 12 aislamientos: por debajo del mínimo de CLSI M39. */
    private static final AntibiogramQueries.Row FEW =
            new AntibiogramQueries.Row(1L, "Escherichia coli", 17L, "Nitrofurantoína", 12, 11, 0, 1, 0, 12);

    @Test
    void calculaPorcentajesEIntervalos() {
        AntibiogramResponseDTO e = AntibiogramService.toEntry(MEROPENEM);

        assertThat(e.totalTested()).isEqualTo(40);
        assertThat(e.susceptiblePercentage()).isEqualTo(95.0);
        assertThat(e.resistancePercentage()).isEqualTo(2.5);
        // El %S está dentro de su intervalo, y el intervalo no es simétrico cerca del 100 %.
        assertThat(e.susceptibleCiLow()).isLessThan(95.0);
        assertThat(e.susceptibleCiHigh()).isGreaterThan(95.0).isLessThanOrEqualTo(100.0);
        assertThat(95.0 - e.susceptibleCiLow()).isGreaterThan(e.susceptibleCiHigh() - 95.0);
        assertThat(e.repeatIsolatesExcluded()).isEqualTo(10);
        assertThat(e.patientIdentifiedPercentage()).isCloseTo(75.0, within(1e-9));
        assertThat(e.sufficientData()).isTrue();
    }

    @Test
    void pordefectoOmiteLasCombinacionesConMenosDeTreinta() {
        List<AntibiogramQueries.Row> rows = List.of(MEROPENEM, FEW);

        assertThat(AntibiogramService.toEntries(rows, false))
                .extracting(AntibiogramResponseDTO::antibioticName).containsExactly("Meropenem");
        assertThat(AntibiogramService.toEntries(rows, true))
                .filteredOn(e -> !e.sufficientData())
                .extracting(AntibiogramResponseDTO::antibioticName).containsExactly("Nitrofurantoína");
    }

    @Test
    void periodosDeLaTendencia() {
        assertThat(AntibiogramService.TrendInterval.MONTH.start(java.time.LocalDate.of(2026, 5, 17)))
                .isEqualTo(java.time.LocalDate.of(2026, 5, 1));
        assertThat(AntibiogramService.TrendInterval.QUARTER.start(java.time.LocalDate.of(2026, 5, 17)))
                .isEqualTo(java.time.LocalDate.of(2026, 4, 1));
        assertThat(AntibiogramService.TrendInterval.QUARTER.start(java.time.LocalDate.of(2024, 9, 1)))
                .isEqualTo(java.time.LocalDate.of(2024, 7, 1));
    }

    @Test
    void laTendenciaSeExpresaEnPuntosPorAnio() {
        // Pendiente 0,02 por mes (2 puntos porcentuales) = 24 puntos por año.
        var trend = AntibiogramService.toTrend(
                new pe.edu.utec.atlasrambackend.stats.CochranArmitage.Result(3.1, 0.002, 0.02, 12, 400),
                AntibiogramService.TrendInterval.MONTH);
        assertThat(trend.changePerYear()).isCloseTo(24.0, within(1e-9));
        assertThat(trend.direction()).isEqualTo("INCREASING");

        var flat = AntibiogramService.toTrend(
                new pe.edu.utec.atlasrambackend.stats.CochranArmitage.Result(-1.2, 0.23, -0.01, 8, 300),
                AntibiogramService.TrendInterval.QUARTER);
        assertThat(flat.direction()).isEqualTo("NO_SIGNIFICANT_TREND");
        assertThat(flat.changePerYear()).isCloseTo(-4.0, within(1e-9));
    }
}
