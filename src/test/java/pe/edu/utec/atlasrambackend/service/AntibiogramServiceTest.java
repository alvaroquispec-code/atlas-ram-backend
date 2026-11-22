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
}
