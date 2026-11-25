package pe.edu.utec.atlasrambackend.service;

import org.junit.jupiter.api.Test;
import pe.edu.utec.atlasrambackend.dto.AntibiogramResponseDTO;
import pe.edu.utec.atlasrambackend.model.Antibiotic;
import pe.edu.utec.atlasrambackend.model.Breakpoint;
import pe.edu.utec.atlasrambackend.model.BreakpointSite;
import pe.edu.utec.atlasrambackend.model.MicQualifier;
import pe.edu.utec.atlasrambackend.model.Microorganism;
import pe.edu.utec.atlasrambackend.repository.AntibiogramQueries;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AntibiogramServiceTest {

    /**
     * E. coli · meropenem: 40 primeros aislamientos (38 S, 1 I, 1 R), 10 repetidos excluidos, 30 identificados,
     * 10 de un laboratorio real.
     */
    private static final AntibiogramQueries.Row MEROPENEM =
            new AntibiogramQueries.Row(1L, "Escherichia coli", 11L, "Meropenem", 40, 38, 1, 1, 30, 50, 10);

    /** Solo 12 aislamientos: por debajo del mínimo de CLSI M39. */
    private static final AntibiogramQueries.Row FEW =
            new AntibiogramQueries.Row(1L, "Escherichia coli", 17L, "Nitrofurantoína", 12, 11, 0, 1, 0, 12, 0);

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
        assertThat(e.realDataPercentage()).isCloseTo(25.0, within(1e-9));
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

    private static Breakpoint ceftriaxone(BreakpointSite site, double sMax, double rMin) {
        Microorganism spn = new Microorganism();
        spn.setId(9L);
        Antibiotic cro = new Antibiotic();
        cro.setId(5L);
        Breakpoint bp = new Breakpoint();
        bp.setMicroorganism(spn);
        bp.setAntibiotic(cro);
        bp.setSite(site);
        bp.setSusceptibleMax(BigDecimal.valueOf(sMax));
        bp.setResistantMin(BigDecimal.valueOf(rMin));
        return bp;
    }

    private static AntibiogramQueries.SiteResultRow cim(String value, boolean first) {
        return new AntibiogramQueries.SiteResultRow(9L, 5L, first, true, false, new BigDecimal(value), null, null);
    }

    @Test
    void ceftriaxonaDelNeumococoSaleEnDosFilasConCadaPuntoDeCorteSobreTodosLosAislamientos() {
        // Fila mezclada de la consulta (no debe sobrevivir) y otra que no depende del sitio (debe quedar igual).
        var mezclada = new AntibiogramQueries.Row(9L, "Streptococcus pneumoniae", 5L, "Ceftriaxona", 4, 3, 1, 0, 4, 5, 0);
        var eritromicina = new AntibiogramQueries.Row(9L, "Streptococcus pneumoniae", 7L, "Eritromicina", 4, 3, 0, 1, 4, 4, 0);
        // CIM 0,25 / 1 / 2 / 4 en primeros aislamientos y un repetido con CIM 1.
        var resultados = List.of(cim("0.25", true), cim("1", true), cim("2", true), cim("4", true), cim("1", false));
        var puntos = List.of(ceftriaxone(BreakpointSite.NON_MENINGITIS, 1, 4), ceftriaxone(BreakpointSite.MENINGITIS, 0.5, 2));

        var filas = AntibiogramService.splitBySite(List.of(mezclada, eritromicina), resultados, puntos);

        assertThat(filas).extracting(AntibiogramQueries.Row::antibioticName)
                .containsExactly("Ceftriaxona (meningitis)", "Ceftriaxona (no meningitis)", "Eritromicina");
        var meningitis = filas.get(0);   // S ≤ 0,5 / R ≥ 2 → 0,25 S · 1 I · 2 R · 4 R
        assertThat(List.of(meningitis.tested(), meningitis.susceptible(), meningitis.intermediate(), meningitis.resistant()))
                .containsExactly(4L, 1L, 1L, 2L);
        assertThat(meningitis.testedAll()).isEqualTo(5);
        assertThat(meningitis.site()).isEqualTo(BreakpointSite.MENINGITIS);
        var otras = filas.get(1);        // S ≤ 1 / R ≥ 4 → 0,25 S · 1 S · 2 I · 4 R
        assertThat(List.of(otras.tested(), otras.susceptible(), otras.intermediate(), otras.resistant()))
                .containsExactly(4L, 2L, 1L, 1L);
        assertThat(filas.get(2)).isSameAs(eritromicina);
    }

    @Test
    void unaCimQueCruzaCategoriasNoEntraEnEsaFila() {
        var fila = new AntibiogramQueries.Row(9L, "Streptococcus pneumoniae", 5L, "Ceftriaxona", 1, 1, 0, 0, 1, 1, 0);
        // "≤ 1": sensible con S ≤ 1, pero con S ≤ 0,5 podría ser 0,5 (S) o 1 (I): no se clasifica.
        var menorIgualUno = new AntibiogramQueries.SiteResultRow(9L, 5L, true, true, false, new BigDecimal("1"),
                MicQualifier.LE, null);

        var filas = AntibiogramService.splitBySite(List.of(fila), List.of(menorIgualUno),
                List.of(ceftriaxone(BreakpointSite.MENINGITIS, 0.5, 2), ceftriaxone(BreakpointSite.NON_MENINGITIS, 1, 4)));

        assertThat(filas).extracting(AntibiogramQueries.Row::antibioticName).containsExactly("Ceftriaxona (no meningitis)");
    }
}
