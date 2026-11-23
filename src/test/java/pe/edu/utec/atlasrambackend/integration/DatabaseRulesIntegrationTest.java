package pe.edu.utec.atlasrambackend.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import pe.edu.utec.atlasrambackend.TestcontainersConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas que viven en la base de datos (triggers, CHECK, índices únicos parciales) probadas contra PostgreSQL
 * real. Cada prueba corre en una transacción que se revierte al final: no deja datos.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DatabaseRulesIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private DbFixtures db;

    @BeforeEach
    void setUp() {
        db = new DbFixtures(jdbc);
    }

    @Test
    void elDistritoDeAgregacionSigueALosTriggers() {
        long d1 = db.district();
        long d2 = db.district();
        long d3 = db.district();
        long facility = db.facility(d1);
        long isolate = db.isolate(facility, db.microorganism(), LocalDate.of(2026, 1, 10), null, "SYNTHETIC");

        // sin distrito de residencia: el del establecimiento
        assertThat(db.aggregationDistrict(isolate)).isEqualTo(d1);

        jdbc.update("update isolate set district_id = ? where id = ?", d2, isolate);
        assertThat(db.aggregationDistrict(isolate)).isEqualTo(d2);

        jdbc.update("update isolate set district_id = null where id = ?", isolate);
        assertThat(db.aggregationDistrict(isolate)).isEqualTo(d1);

        // el establecimiento cambia de distrito: el aislamiento sin distrito propio lo sigue
        jdbc.update("update facility set district_id = ? where id = ?", d3, facility);
        assertThat(db.aggregationDistrict(isolate)).isEqualTo(d3);
    }

    @Test
    void unArchivoActivoNoSePuedeCargarDosVecesPeroSiTrasRevertirlo() {
        long facility = db.facility(db.district());
        long user = db.user();
        String sha = "a".repeat(64);
        long first = db.upload(facility, user, "COMPLETED", sha);

        db.expectViolation(
                "insert into data_upload (file_name, status, started_at, facility_id, uploaded_by, file_sha256) values ('x.csv', 'PENDING', now(), ?, ?, ?)",
                facility, user, sha);

        jdbc.update("update data_upload set status = 'REVERTED' where id = ?", first);
        db.upload(facility, user, "PENDING", sha);   // ya no choca
    }

    @Test
    void restriccionesDeValoresPermitidos() {
        long district = db.district();
        long facility = db.facility(district);

        db.expectViolation("update facility set renipress_code = 'ABC' where id = ?", facility);
        jdbc.update("update facility set renipress_code = '12345678' where id = ?", facility);

        db.expectViolation(
                "insert into isolate (collection_date, specimen_type, facility_id, microorganism_id, data_origin) values (current_date, 'BLOOD', ?, ?, 'FALSO')",
                facility, db.microorganism());

        long item = db.stockItem(facility, db.antibiotic("Carbapenémico", "WATCH"));
        db.expectViolation(
                "insert into stock_lot (stock_item_id, lot_number, expiry_date, quantity_received, quantity_on_hand, received_on) values (?, 'L1', current_date + 100, 5, 6, current_date)",
                item);
        long lot = db.lot(item, "L2", LocalDate.now().plusDays(100), 5);
        db.expectViolation("update stock_lot set quantity_on_hand = -1 where id = ?", lot);

        // los valores nuevos de los enums sí se aceptan (V6 y V9 recrearon las restricciones)
        jdbc.update("update app_user set role = 'PHARMACIST' where id = ?", db.user());
        long upload = db.upload(facility, db.user(), "COMPLETED", "b".repeat(64));
        jdbc.update("update data_upload set status = 'REVERTED' where id = ?", upload);
    }

    @Test
    void unCodigoDeAislamientoDelLaboratorioNoSeRepiteEnElEstablecimiento() {
        long facility = db.facility(db.district());
        long micro = db.microorganism();
        long isolate = db.isolate(facility, micro, LocalDate.of(2026, 2, 1), null, "SYNTHETIC");
        jdbc.update("update isolate set lab_isolate_id = 'M-1' where id = ?", isolate);

        db.expectViolation(
                "insert into isolate (collection_date, specimen_type, facility_id, microorganism_id, lab_isolate_id) values (current_date, 'BLOOD', ?, ?, 'M-1')",
                facility, micro);
    }
}
