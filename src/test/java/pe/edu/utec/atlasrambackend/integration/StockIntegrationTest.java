package pe.edu.utec.atlasrambackend.integration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.dto.StockConsumptionDTO;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.repository.LogisticsQueries;
import pe.edu.utec.atlasrambackend.service.StockService;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El kardex contra PostgreSQL real: FEFO, rechazo sin escrituras parciales y agregación mensual. */
@SpringBootTest
@Transactional
class StockIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Lima"));

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StockService stockService;

    @Autowired
    private LogisticsQueries queries;

    @Autowired
    private EntityManager entityManager;

    private long facility;
    private long item;
    private long early;
    private long late;

    @BeforeEach
    void setUp() {
        DbFixtures db = new DbFixtures(jdbc);
        facility = db.facility(db.district());
        item = db.stockItem(facility, db.antibiotic("Carbapenémico", "WATCH"));
        late = db.lot(item, "TARDE", TODAY.plusDays(400), 10);
        early = db.lot(item, "PRONTO", TODAY.plusDays(40), 5);
        db.lot(item, "VENCIDO", TODAY.minusDays(1), 50);   // no se puede usar
    }

    private int onHand(long lotId) {
        entityManager.flush();
        return jdbc.queryForObject("select quantity_on_hand from stock_lot where id = ?", Integer.class, lotId);
    }

    @Test
    void elConsumoSaleDelLoteQueVenceAntesYNoTocaElVencido() {
        stockService.consume(item, new StockConsumptionDTO(8, TODAY, null), null);

        assertThat(onHand(early)).isZero();
        assertThat(onHand(late)).isEqualTo(7);
        Integer movements = jdbc.queryForObject(
                "select count(*) from stock_movement where stock_item_id = ? and type = 'CONSUMPTION'", Integer.class, item);
        assertThat(movements).isEqualTo(2);
    }

    @Test
    void unConsumoMayorAlSaldoUtilizableNoEscribeNada() {
        // hay 15 utilizables (el lote vencido no cuenta aunque tenga 50)
        assertThatThrownBy(() -> stockService.consume(item, new StockConsumptionDTO(16, TODAY, null), null))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(onHand(early)).isEqualTo(5);
        assertThat(onHand(late)).isEqualTo(10);
    }

    @Test
    void elConsumoMensualSeAgregaPorItemYMes() {
        stockService.consume(item, new StockConsumptionDTO(3, TODAY, null), null);
        stockService.consume(item, new StockConsumptionDTO(4, TODAY, null), null);
        entityManager.flush();

        LocalDate month = TODAY.withDayOfMonth(1);
        List<LogisticsQueries.MonthlyRow> rows = queries.monthlyConsumption(facility, month, month.plusMonths(1));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).itemId()).isEqualTo(item);
        assertThat(rows.get(0).units()).isEqualTo(7);
    }
}
