package pe.edu.utec.atlasrambackend.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.model.DataOrigin;
import pe.edu.utec.atlasrambackend.repository.AntibiogramQueries;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las consultas SQL nativas del antibiograma (CLSI M39), la tendencia y el WISCA, contra PostgreSQL real.
 * Se filtra por el establecimiento de la prueba para no mezclar con otros datos de la base.
 */
@SpringBootTest
@Transactional
class AntibiogramQueriesIntegrationTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 6, 30);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AntibiogramQueries queries;

    private long facility;
    private long micro;
    private long meropenem;

    @BeforeEach
    void setUp() {
        DbFixtures db = new DbFixtures(jdbc);
        facility = db.facility(db.district());
        micro = db.microorganism();
        meropenem = db.antibiotic("Carbapenémico", "WATCH");

        // Paciente P1: tres cultivos en tres meses. El primero (enero) es sensible; los repetidos, resistentes.
        db.result(db.isolate(facility, micro, LocalDate.of(2026, 1, 10), "P1", "SYNTHETIC"), meropenem, "SUSCEPTIBLE");
        db.result(db.isolate(facility, micro, LocalDate.of(2026, 2, 10), "P1", "SYNTHETIC"), meropenem, "RESISTANT");
        db.result(db.isolate(facility, micro, LocalDate.of(2026, 3, 10), "P1", "SYNTHETIC"), meropenem, "RESISTANT");
        // Paciente P2: un cultivo real, resistente. Un aislamiento sin paciente cuenta como paciente propio.
        db.result(db.isolate(facility, micro, LocalDate.of(2026, 2, 20), "P2", "REAL"), meropenem, "RESISTANT");
        db.result(db.isolate(facility, micro, LocalDate.of(2026, 3, 5), null, "SYNTHETIC"), meropenem, "SUSCEPTIBLE");
    }

    @Test
    void soloCuentaElPrimerAislamientoDeCadaPaciente() {
        List<AntibiogramQueries.Row> rows = queries.antibiogram(micro, null, List.of(facility), FROM, TO, null);

        assertThat(rows).hasSize(1);
        AntibiogramQueries.Row row = rows.get(0);
        assertThat(row.tested()).isEqualTo(3);        // P1 (enero), P2 y el aislamiento sin paciente
        assertThat(row.testedAll()).isEqualTo(5);     // 2 repetidos de P1 excluidos
        assertThat(row.susceptible()).isEqualTo(2);
        assertThat(row.resistant()).isEqualTo(1);
        assertThat(row.identified()).isEqualTo(2);
        assertThat(row.realCount()).isEqualTo(1);
    }

    @Test
    void elFiltroDeProcedenciaSeparaLosDatosReales() {
        List<AntibiogramQueries.Row> real = queries.antibiogram(micro, null, List.of(facility), FROM, TO, DataOrigin.REAL);

        assertThat(real).hasSize(1);
        assertThat(real.get(0).tested()).isEqualTo(1);
        assertThat(real.get(0).resistant()).isEqualTo(1);
    }

    @Test
    void enLaTendenciaElPrimerAislamientoSeTomaPorPeriodo() {
        List<AntibiogramQueries.PeriodRow> months =
                queries.trend(micro, meropenem, null, List.of(facility), FROM, TO, "month", null);

        assertThat(months).extracting(AntibiogramQueries.PeriodRow::period)
                .containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1));
        // febrero: P1 y P2, ambos resistentes; marzo: P1 (R) y el aislamiento sin paciente (S)
        assertThat(months).extracting(AntibiogramQueries.PeriodRow::tested).containsExactly(1L, 2L, 2L);
        assertThat(months).extracting(AntibiogramQueries.PeriodRow::resistant).containsExactly(0L, 2L, 1L);
    }

    @Test
    void elWiscaRecibeElPerfilDeCadaPrimerAislamiento() {
        List<AntibiogramQueries.ProfileRow> profiles =
                queries.profiles("BLOOD", null, List.of(facility), FROM, TO, null);

        assertThat(profiles).hasSize(3);
        assertThat(profiles).extracting(AntibiogramQueries.ProfileRow::interpretation)
                .containsExactlyInAnyOrder("SUSCEPTIBLE", "RESISTANT", "SUSCEPTIBLE");
    }
}
