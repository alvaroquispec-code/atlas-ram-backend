package pe.edu.utec.atlasrambackend.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import pe.edu.utec.atlasrambackend.TestcontainersConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.utec.atlasrambackend.dto.StockConsumptionDTO;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.service.StockService;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dos farmacéuticos descuentan a la vez las últimas unidades de un lote. El bloqueo pesimista (SELECT … FOR
 * UPDATE) hace que el segundo espere al primero y vuelva a leer el saldo: uno se atiende y el otro se rechaza,
 * nunca quedan unidades negativas. Esta prueba necesita transacciones confirmadas, así que limpia al final.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StockConcurrencyIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Lima"));

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StockService stockService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long district;
    private long facility;
    private long antibiotic;
    private long item;
    private long lot;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            DbFixtures db = new DbFixtures(jdbc);
            district = db.district();
            facility = db.facility(district);
            antibiotic = db.antibiotic("Carbapenémico", "WATCH");
            item = db.stockItem(facility, antibiotic);
            lot = db.lot(item, "ULTIMO", TODAY.plusDays(100), 6);
        });
    }

    @AfterEach
    void cleanUp() {
        // stock_lot y stock_movement se borran en cascada con el ítem
        jdbc.update("delete from stock_item where id = ?", item);
        jdbc.update("delete from antibiotic where id = ?", antibiotic);
        jdbc.update("delete from facility where id = ?", facility);
        jdbc.update("delete from district where id = ?", district);
    }

    @Test
    void dosConsumosSimultaneosNoDejanStockNegativo() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> consumeFive = () -> {
            start.await();
            try {
                stockService.consume(item, new StockConsumptionDTO(5, TODAY, null), null);
                return true;
            } catch (BusinessRuleException e) {
                return false;
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>(List.of(pool.submit(consumeFive), pool.submit(consumeFive)));
            start.countDown();
            List<Boolean> outcomes = new ArrayList<>();
            for (Future<Boolean> r : results) {
                outcomes.add(r.get(30, TimeUnit.SECONDS));
            }

            assertThat(outcomes).containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject("select quantity_on_hand from stock_lot where id = ?", Integer.class, lot))
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
