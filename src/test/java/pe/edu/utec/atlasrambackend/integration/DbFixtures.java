package pe.edu.utec.atlasrambackend.integration;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Datos mínimos para las pruebas de integración, creados con SQL directo. Cada prueba crea los suyos con
 * códigos únicos, así que no depende del seed (en CI la base está vacía) ni choca con él (en local está lleno).
 */
final class DbFixtures {

    private final JdbcTemplate jdbc;
    private final String tag = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    private int counter;

    DbFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private String code(String prefix, int max) {
        String value = prefix + tag + (counter++);
        return value.length() > max ? value.substring(value.length() - max) : value;
    }

    long district() {
        return jdbc.queryForObject("insert into district (name, ubigeo) values (?, ?) returning id", Long.class,
                "Distrito " + code("D", 20), code("9", 6));
    }

    long facility(long districtId) {
        return jdbc.queryForObject("insert into facility (code, name, district_id) values (?, ?, ?) returning id",
                Long.class, code("F", 20), "Hospital de prueba", districtId);
    }

    long microorganism() {
        return jdbc.queryForObject(
                "insert into microorganism (code, genus, species, gram_stain) values (?, 'Testus', 'probus', 'NEGATIVE') returning id",
                Long.class, code("m", 10).toLowerCase());
    }

    long antibiotic(String antibioticClass, String aware) {
        return jdbc.queryForObject(
                "insert into antibiotic (code, name, antibiotic_class, aware_category) values (?, 'Antibiótico de prueba', ?, ?) returning id",
                Long.class, code("A", 10), antibioticClass, aware);
    }

    long user() {
        return jdbc.queryForObject(
                "insert into app_user (email, full_name, password_hash, role, active, created_at) values (?, 'Prueba', 'x', 'LAB_TECHNICIAN', true, now()) returning id",
                Long.class, code("u", 30).toLowerCase() + "@atlasram.test");
    }

    long upload(long facilityId, long userId, String status, String sha256) {
        return jdbc.queryForObject(
                "insert into data_upload (file_name, status, started_at, facility_id, uploaded_by, file_sha256) values ('prueba.csv', ?, now(), ?, ?, ?) returning id",
                Long.class, status, facilityId, userId, sha256);
    }

    long isolate(long facilityId, long microorganismId, LocalDate date, String patientKey, String origin) {
        return jdbc.queryForObject(
                "insert into isolate (collection_date, specimen_type, facility_id, microorganism_id, patient_key, data_origin) values (?, 'BLOOD', ?, ?, ?, ?) returning id",
                Long.class, date, facilityId, microorganismId, patientKey, origin);
    }

    void result(long isolateId, long antibioticId, String interpretation) {
        jdbc.update("insert into susceptibility_result (isolate_id, antibiotic_id, interpretation) values (?, ?, ?)",
                isolateId, antibioticId, interpretation);
    }

    long stockItem(long facilityId, long antibioticId) {
        return jdbc.queryForObject(
                "insert into stock_item (facility_id, antibiotic_id, presentation, grams_per_unit, ddd_grams, unit_cost, ven_class) values (?, ?, 'Vial 1 g', 1, 3, 25, 'VITAL') returning id",
                Long.class, facilityId, antibioticId);
    }

    long lot(long itemId, String number, LocalDate expiry, int quantity) {
        return jdbc.queryForObject(
                "insert into stock_lot (stock_item_id, lot_number, expiry_date, quantity_received, quantity_on_hand, received_on) values (?, ?, ?, ?, ?, current_date - 30) returning id",
                Long.class, itemId, number, expiry, quantity, quantity);
    }

    /** La sentencia debe violar una restricción. El savepoint evita que el error aborte la transacción de la prueba. */
    void expectViolation(String sql, Object... args) {
        jdbc.execute("savepoint antes_de_violar");
        assertThatThrownBy(() -> jdbc.update(sql, args)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.execute("rollback to savepoint antes_de_violar");
    }

    Long aggregationDistrict(long isolateId) {
        return jdbc.queryForObject("select aggregation_district_id from isolate where id = ?", Long.class, isolateId);
    }
}
