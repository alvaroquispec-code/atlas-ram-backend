package pe.edu.utec.atlasrambackend.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Agregaciones del kardex para las series de demanda y el indicador DDD. */
@Repository
public class LogisticsQueries {

    private static final String MONTHLY_CONSUMPTION = """
            select m.stock_item_id, cast(date_trunc('month', m.movement_date) as date) as month,
                   sum(m.quantity) as units
            from stock_movement m
            join stock_item s on s.id = m.stock_item_id
            where s.facility_id = :facilityId
              and m.type = 'CONSUMPTION'
              and m.movement_date >= :from and m.movement_date < :to
            group by m.stock_item_id, month
            """;

    private static final String FIRST_MOVEMENT = """
            select m.stock_item_id, min(m.movement_date) as first_date
            from stock_movement m
            join stock_item s on s.id = m.stock_item_id
            where s.facility_id = :facilityId
            group by m.stock_item_id
            """;

    private static final String DDD_CONSUMPTION = """
            select s.id as item_id, a.code, a.name, s.presentation, a.aware_category,
                   s.grams_per_unit, s.ddd_grams, sum(m.quantity) as units
            from stock_movement m
            join stock_item s on s.id = m.stock_item_id
            join antibiotic a on a.id = s.antibiotic_id
            where s.facility_id = :facilityId
              and m.type = 'CONSUMPTION'
              and m.movement_date between :from and :to
            group by s.id, a.code, a.name, s.presentation, a.aware_category, s.grams_per_unit, s.ddd_grams
            order by a.code
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public LogisticsQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record MonthlyRow(long itemId, LocalDate month, int units) {
    }

    public record FirstMovementRow(long itemId, LocalDate firstDate) {
    }

    public record DddRow(long itemId, String code, String name, String presentation, String awareCategory,
                         BigDecimal gramsPerUnit, BigDecimal dddGrams, long units) {
    }

    /** Consumo por ítem y mes, en [from, to). */
    public List<MonthlyRow> monthlyConsumption(Long facilityId, LocalDate from, LocalDate to) {
        var params = new MapSqlParameterSource()
                .addValue("facilityId", facilityId).addValue("from", from).addValue("to", to);
        return jdbc.query(MONTHLY_CONSUMPTION, params, (rs, i) -> new MonthlyRow(
                rs.getLong("stock_item_id"), rs.getObject("month", LocalDate.class), rs.getInt("units")));
    }

    public List<FirstMovementRow> firstMovements(Long facilityId) {
        var params = new MapSqlParameterSource().addValue("facilityId", facilityId);
        return jdbc.query(FIRST_MOVEMENT, params, (rs, i) -> new FirstMovementRow(
                rs.getLong("stock_item_id"), rs.getObject("first_date", LocalDate.class)));
    }

    /** Consumo por ítem en [from, to], ambos inclusive. */
    public List<DddRow> dddConsumption(Long facilityId, LocalDate from, LocalDate to) {
        var params = new MapSqlParameterSource()
                .addValue("facilityId", facilityId).addValue("from", from).addValue("to", to);
        return jdbc.query(DDD_CONSUMPTION, params, (rs, i) -> new DddRow(
                rs.getLong("item_id"), rs.getString("code"), rs.getString("name"), rs.getString("presentation"),
                rs.getString("aware_category"), rs.getBigDecimal("grams_per_unit"), rs.getBigDecimal("ddd_grams"),
                rs.getLong("units")));
    }
}
