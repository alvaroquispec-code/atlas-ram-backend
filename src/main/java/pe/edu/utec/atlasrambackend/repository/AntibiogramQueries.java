package pe.edu.utec.atlasrambackend.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Consultas del antibiograma acumulado según CLSI M39: solo el PRIMER aislamiento de cada paciente y
 * especie en el periodo, sin importar el tipo de muestra ni su perfil de sensibilidad.
 *
 * Por qué: un paciente con una infección difícil tiene muchos cultivos repetidos, y suelen ser los más
 * resistentes. Contarlos todos infla la resistencia y el antibiograma deja de describir "al próximo
 * paciente que llegue", que es lo que necesita el médico que elige el tratamiento empírico.
 *
 * Los aislamientos sin paciente identificado (patient_key nulo) cuentan cada uno como un paciente.
 * Se escribe en SQL nativo porque la regla necesita una función de ventana (row_number), que JPQL no tiene.
 */
@Repository
public class AntibiogramQueries {

    /** Aislamientos del periodo y filtros, numerados por paciente y especie en orden de fecha. */
    private static final String RANKED_ISOLATES = """
            with eligible as (
                select i.id, i.microorganism_id, i.patient_key, i.collection_date,
                       coalesce(i.district_id, f.district_id) as district_id
                from isolate i
                join facility f on f.id = i.facility_id
                where i.collection_date between :from and :to
                  and (cast(:microorganismId as bigint) is null or i.microorganism_id = :microorganismId)
                  and (cast(:districtId as bigint) is null
                       or coalesce(i.district_id, f.district_id) = :districtId)
                  and (:allFacilities or i.facility_id in (:facilityIds))
                  and (cast(:specimen as varchar) is null or i.specimen_type = :specimen)
            ),
            ranked as (
                select e.*,
                       row_number() over (
                           partition by coalesce(e.patient_key, 'aislamiento:' || e.id), e.microorganism_id
                           order by e.collection_date, e.id) as rn
                from eligible e
            )
            """;

    private static final String ANTIBIOGRAM = RANKED_ISOLATES + """
            select x.microorganism_id, m.genus, m.species, r.antibiotic_id, a.name as antibiotic_name,
                   count(*) filter (where x.rn = 1)                                         as tested,
                   count(*) filter (where x.rn = 1 and r.interpretation = 'SUSCEPTIBLE')    as susceptible,
                   count(*) filter (where x.rn = 1 and r.interpretation = 'INTERMEDIATE')   as intermediate,
                   count(*) filter (where x.rn = 1 and r.interpretation = 'RESISTANT')      as resistant,
                   count(*) filter (where x.rn = 1 and x.patient_key is not null)           as identified,
                   count(*)                                                                 as tested_all
            from ranked x
            join susceptibility_result r on r.isolate_id = x.id
            join microorganism m on m.id = x.microorganism_id
            join antibiotic a on a.id = r.antibiotic_id
            group by x.microorganism_id, m.genus, m.species, r.antibiotic_id, a.name
            having count(*) filter (where x.rn = 1) > 0
            order by m.genus, m.species, a.name
            """;

    private static final String BY_DISTRICT = RANKED_ISOLATES + """
            select x.district_id,
                   count(*) filter (where x.rn = 1)                                         as tested,
                   count(*) filter (where x.rn = 1 and r.interpretation = 'SUSCEPTIBLE')    as susceptible,
                   count(*) filter (where x.rn = 1 and r.interpretation = 'RESISTANT')      as resistant
            from ranked x
            join susceptibility_result r on r.isolate_id = x.id and r.antibiotic_id = :antibioticId
            where x.district_id is not null
            group by x.district_id
            """;

    /**
     * Serie temporal de un par microorganismo-antibiótico. La regla del primer aislamiento se aplica
     * dentro de cada periodo (mes o trimestre): el mismo paciente puede aportar un aislamiento en enero
     * y otro en marzo, pero no dos en enero.
     */
    private static final String TREND = """
            with eligible as (
                select i.id, i.patient_key, i.collection_date,
                       cast(date_trunc(:unit, cast(i.collection_date as timestamp)) as date) as period
                from isolate i
                join facility f on f.id = i.facility_id
                where i.collection_date between :from and :to
                  and i.microorganism_id = :microorganismId
                  and (cast(:districtId as bigint) is null
                       or coalesce(i.district_id, f.district_id) = :districtId)
                  and (:allFacilities or i.facility_id in (:facilityIds))
            ),
            ranked as (
                select e.*,
                       row_number() over (
                           partition by coalesce(e.patient_key, 'aislamiento:' || e.id), e.period
                           order by e.collection_date, e.id) as rn
                from eligible e
            )
            select x.period,
                   count(*)                                                  as tested,
                   count(*) filter (where r.interpretation = 'SUSCEPTIBLE')  as susceptible,
                   count(*) filter (where r.interpretation = 'RESISTANT')    as resistant
            from ranked x
            join susceptibility_result r on r.isolate_id = x.id and r.antibiotic_id = :antibioticId
            where x.rn = 1
            group by x.period
            order by x.period
            """;

    public record PeriodRow(LocalDate period, long tested, long susceptible, long resistant) {
    }

    /** Perfil completo de cada primer aislamiento de un tipo de muestra: insumo del WISCA. */
    private static final String PROFILES = RANKED_ISOLATES + """
            select x.id as isolate_id, x.microorganism_id, m.code as microorganism_code, m.genus, m.species,
                   a.code as antibiotic_code, r.interpretation
            from ranked x
            join microorganism m on m.id = x.microorganism_id
            join susceptibility_result r on r.isolate_id = x.id
            join antibiotic a on a.id = r.antibiotic_id
            where x.rn = 1
            order by x.id
            """;

    public record ProfileRow(Long isolateId, Long microorganismId, String microorganismCode,
                             String microorganismName, String antibioticCode, String interpretation) {
    }

    /** Una fila del antibiograma: un par microorganismo-antibiótico. */
    public record Row(Long microorganismId, String microorganismName, Long antibioticId, String antibioticName,
                      long tested, long susceptible, long intermediate, long resistant,
                      long identified, long testedAll) {
    }

    public record DistrictRow(Long districtId, long tested, long susceptible, long resistant) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public AntibiogramQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** facilityIds null = todos los establecimientos. */
    public List<Row> antibiogram(Long microorganismId, Long districtId, Collection<Long> facilityIds,
                                 LocalDate from, LocalDate to) {
        return jdbc.query(ANTIBIOGRAM, params(microorganismId, districtId, facilityIds, from, to),
                AntibiogramQueries::mapRow);
    }

    public List<DistrictRow> byDistrict(Long microorganismId, Long antibioticId, LocalDate from, LocalDate to) {
        MapSqlParameterSource params = params(microorganismId, null, null, from, to)
                .addValue("antibioticId", antibioticId);
        return jdbc.query(BY_DISTRICT, params, (rs, i) -> new DistrictRow(
                rs.getLong("district_id"), rs.getLong("tested"), rs.getLong("susceptible"), rs.getLong("resistant")));
    }

    /** Primeros aislamientos de un tipo de muestra con todos sus resultados. facilityIds null = todos. */
    public List<ProfileRow> profiles(String specimen, Long districtId, Collection<Long> facilityIds,
                                     LocalDate from, LocalDate to) {
        MapSqlParameterSource params = params(null, districtId, facilityIds, from, to)
                .addValue("specimen", specimen, java.sql.Types.VARCHAR);
        return jdbc.query(PROFILES, params, (rs, i) -> {
            String species = rs.getString("species");
            return new ProfileRow(rs.getLong("isolate_id"), rs.getLong("microorganism_id"),
                    rs.getString("microorganism_code"),
                    species == null ? rs.getString("genus") : rs.getString("genus") + " " + species,
                    rs.getString("antibiotic_code"), rs.getString("interpretation"));
        });
    }

    /** unit: "month" o "quarter" (argumento de date_trunc). facilityIds null = todos. */
    public List<PeriodRow> trend(Long microorganismId, Long antibioticId, Long districtId,
                                 Collection<Long> facilityIds, LocalDate from, LocalDate to, String unit) {
        MapSqlParameterSource params = params(microorganismId, districtId, facilityIds, from, to)
                .addValue("antibioticId", antibioticId)
                .addValue("unit", unit);
        return jdbc.query(TREND, params, (rs, i) -> new PeriodRow(
                rs.getObject("period", LocalDate.class), rs.getLong("tested"),
                rs.getLong("susceptible"), rs.getLong("resistant")));
    }

    private static MapSqlParameterSource params(Long microorganismId, Long districtId, Collection<Long> facilityIds,
                                                LocalDate from, LocalDate to) {
        boolean all = facilityIds == null;
        return new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to)
                .addValue("microorganismId", microorganismId, java.sql.Types.BIGINT)
                .addValue("districtId", districtId, java.sql.Types.BIGINT)
                .addValue("allFacilities", all)
                .addValue("specimen", null, java.sql.Types.VARCHAR)
                // "in ()" vacío es SQL inválido; con allFacilities = true esta lista no se evalúa.
                .addValue("facilityIds", all || facilityIds.isEmpty() ? List.of(-1L) : facilityIds);
    }

    private static Row mapRow(ResultSet rs, int i) throws SQLException {
        String species = rs.getString("species");
        String name = species == null ? rs.getString("genus") : rs.getString("genus") + " " + species;
        return new Row(rs.getLong("microorganism_id"), name, rs.getLong("antibiotic_id"),
                rs.getString("antibiotic_name"), rs.getLong("tested"), rs.getLong("susceptible"),
                rs.getLong("intermediate"), rs.getLong("resistant"), rs.getLong("identified"),
                rs.getLong("tested_all"));
    }
}
