package pe.edu.utec.atlasrambackend.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.edu.utec.atlasrambackend.model.BreakpointSite;
import pe.edu.utec.atlasrambackend.model.DataOrigin;
import pe.edu.utec.atlasrambackend.model.MicQualifier;
import pe.edu.utec.atlasrambackend.model.Ward;

import java.math.BigDecimal;
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
                select i.id, i.microorganism_id, i.patient_key, i.collection_date, i.data_origin,
                       i.aggregation_district_id as district_id
                from isolate i
                where i.collection_date between :from and :to
                  and (cast(:microorganismId as bigint) is null or i.microorganism_id = :microorganismId)
                  and (cast(:districtId as bigint) is null or i.aggregation_district_id = :districtId)
                  and (:allFacilities or i.facility_id in (:facilityIds))
                  and (cast(:specimen as varchar) is null or i.specimen_type = :specimen)
                  and (cast(:origin as varchar) is null or i.data_origin = :origin)
                  and (cast(:ward as varchar) is null or i.ward = :ward)
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
                   count(*) filter (where x.rn = 1 and x.data_origin = 'REAL')              as real_count,
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
                where i.collection_date between :from and :to
                  and i.microorganism_id = :microorganismId
                  and (cast(:districtId as bigint) is null or i.aggregation_district_id = :districtId)
                  and (:allFacilities or i.facility_id in (:facilityIds))
                  and (cast(:origin as varchar) is null or i.data_origin = :origin)
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

    /** CIM y halo de un primer aislamiento, con la clave por la que se agrupa (distrito o periodo). */
    public record KeyedResult<K>(K key, BigDecimal micValue, MicQualifier micQualifier, Integer diskMm) {
    }

    /** Como BY_DISTRICT, pero con la CIM de cada primer aislamiento para clasificarla con otro punto de corte. */
    private static final String DISTRICT_RESULTS = RANKED_ISOLATES + """
            select x.district_id as key, r.mic_value, r.mic_qualifier, r.disk_diffusion_mm
            from ranked x
            join susceptibility_result r on r.isolate_id = x.id and r.antibiotic_id = :antibioticId
            where x.district_id is not null and x.rn = 1
            """;

    /** Como TREND, pero con la CIM de cada primer aislamiento del periodo. */
    private static final String TREND_RESULTS = TREND.substring(0, TREND.indexOf("select x.period,")) + """
            select x.period as key, r.mic_value, r.mic_qualifier, r.disk_diffusion_mm
            from ranked x
            join susceptibility_result r on r.isolate_id = x.id and r.antibiotic_id = :antibioticId
            where x.rn = 1
            """;

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

    /**
     * Una fila del antibiograma: un par microorganismo-antibiótico. site es null salvo en los pares con
     * punto de corte por sitio de infección, que salen en dos filas (meningitis y no meningitis).
     */
    public record Row(Long microorganismId, String microorganismName, Long antibioticId, String antibioticName,
                      long tested, long susceptible, long intermediate, long resistant,
                      long identified, long testedAll, long realCount, BreakpointSite site) {

        public Row(Long microorganismId, String microorganismName, Long antibioticId, String antibioticName,
                   long tested, long susceptible, long intermediate, long resistant,
                   long identified, long testedAll, long realCount) {
            this(microorganismId, microorganismName, antibioticId, antibioticName, tested, susceptible,
                    intermediate, resistant, identified, testedAll, realCount, null);
        }
    }

    /**
     * Resultado individual de un par con puntos de corte por sitio, con su CIM y halo, para volver a
     * clasificarlo con cada uno. first = es el primer aislamiento del paciente (regla de M39).
     */
    public record SiteResultRow(Long microorganismId, Long antibioticId, boolean first, boolean identified,
                                boolean real, BigDecimal micValue, MicQualifier micQualifier, Integer diskMm) {
    }

    public record DistrictRow(Long districtId, long tested, long susceptible, long resistant) {
    }

    /** Resultados (todos, no solo el primero) de los pares que tienen puntos de corte por sitio en la edición. */
    private static final String SITE_RESULTS = RANKED_ISOLATES + """
            select x.microorganism_id, r.antibiotic_id, x.rn = 1 as first, x.patient_key is not null as identified,
                   x.data_origin = 'REAL' as is_real, r.mic_value, r.mic_qualifier, r.disk_diffusion_mm
            from ranked x
            join susceptibility_result r on r.isolate_id = x.id
            where exists (select 1 from breakpoint b
                          where b.microorganism_id = x.microorganism_id and b.antibiotic_id = r.antibiotic_id
                            and b.standard = :standard and b.version = :version and b.site <> 'ANY')
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AntibiogramQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** facilityIds null = todos los establecimientos. origin null = toda procedencia. */
    public List<Row> antibiogram(Long microorganismId, Long districtId, Collection<Long> facilityIds,
                                 LocalDate from, LocalDate to, DataOrigin origin) {
        return antibiogram(microorganismId, districtId, facilityIds, from, to, origin, null);
    }

    /** ward null = todos los servicios; con un servicio, solo los aislamientos de ese servicio (p. ej. UCI). */
    public List<Row> antibiogram(Long microorganismId, Long districtId, Collection<Long> facilityIds,
                                 LocalDate from, LocalDate to, DataOrigin origin, Ward ward) {
        return jdbc.query(ANTIBIOGRAM, withWard(params(microorganismId, districtId, facilityIds, from, to, origin), ward),
                AntibiogramQueries::mapRow);
    }

    private static MapSqlParameterSource withWard(MapSqlParameterSource params, Ward ward) {
        return params.addValue("ward", ward == null ? null : ward.name(), java.sql.Types.VARCHAR);
    }

    /** facilityIds null = todos los establecimientos. origin null = toda procedencia. */
    public List<SiteResultRow> siteResults(Long microorganismId, Long districtId, Collection<Long> facilityIds,
                                           LocalDate from, LocalDate to, DataOrigin origin, Ward ward,
                                           String standard, String version) {
        MapSqlParameterSource params = withWard(params(microorganismId, districtId, facilityIds, from, to, origin), ward)
                .addValue("standard", standard)
                .addValue("version", version);
        return jdbc.query(SITE_RESULTS, params, (rs, i) -> {
            String qualifier = rs.getString("mic_qualifier");
            Integer disk = rs.getObject("disk_diffusion_mm", Integer.class);
            return new SiteResultRow(rs.getLong("microorganism_id"), rs.getLong("antibiotic_id"),
                    rs.getBoolean("first"), rs.getBoolean("identified"), rs.getBoolean("is_real"),
                    rs.getBigDecimal("mic_value"), qualifier == null ? null : MicQualifier.valueOf(qualifier),
                    disk);
        });
    }

    public List<DistrictRow> byDistrict(Long microorganismId, Long antibioticId, LocalDate from, LocalDate to,
                                        DataOrigin origin) {
        MapSqlParameterSource params = params(microorganismId, null, null, from, to, origin)
                .addValue("antibioticId", antibioticId);
        return jdbc.query(BY_DISTRICT, params, (rs, i) -> new DistrictRow(
                rs.getLong("district_id"), rs.getLong("tested"), rs.getLong("susceptible"), rs.getLong("resistant")));
    }

    /** Primeros aislamientos de un tipo de muestra con todos sus resultados. facilityIds null = todos. */
    public List<ProfileRow> profiles(String specimen, Long districtId, Collection<Long> facilityIds,
                                     LocalDate from, LocalDate to, DataOrigin origin) {
        return profiles(specimen, districtId, facilityIds, from, to, origin, null);
    }

    public List<ProfileRow> profiles(String specimen, Long districtId, Collection<Long> facilityIds,
                                     LocalDate from, LocalDate to, DataOrigin origin, Ward ward) {
        MapSqlParameterSource params = withWard(params(null, districtId, facilityIds, from, to, origin), ward)
                .addValue("specimen", specimen, java.sql.Types.VARCHAR);
        return jdbc.query(PROFILES, params, (rs, i) -> {
            String species = rs.getString("species");
            return new ProfileRow(rs.getLong("isolate_id"), rs.getLong("microorganism_id"),
                    rs.getString("microorganism_code"),
                    species == null ? rs.getString("genus") : rs.getString("genus") + " " + species,
                    rs.getString("antibiotic_code"), rs.getString("interpretation"));
        });
    }

    public List<KeyedResult<Long>> districtResults(Long microorganismId, Long antibioticId, LocalDate from,
                                                   LocalDate to, DataOrigin origin) {
        MapSqlParameterSource params = params(microorganismId, null, null, from, to, origin)
                .addValue("antibioticId", antibioticId);
        return jdbc.query(DISTRICT_RESULTS, params, (rs, i) -> keyed(rs, rs.getLong("key")));
    }

    public List<KeyedResult<LocalDate>> trendResults(Long microorganismId, Long antibioticId, Long districtId,
                                                     Collection<Long> facilityIds, LocalDate from, LocalDate to,
                                                     String unit, DataOrigin origin) {
        MapSqlParameterSource params = params(microorganismId, districtId, facilityIds, from, to, origin)
                .addValue("antibioticId", antibioticId)
                .addValue("unit", unit);
        return jdbc.query(TREND_RESULTS, params, (rs, i) -> keyed(rs, rs.getObject("key", LocalDate.class)));
    }

    private static <K> KeyedResult<K> keyed(ResultSet rs, K key) throws SQLException {
        String qualifier = rs.getString("mic_qualifier");
        return new KeyedResult<>(key, rs.getBigDecimal("mic_value"),
                qualifier == null ? null : MicQualifier.valueOf(qualifier),
                rs.getObject("disk_diffusion_mm", Integer.class));
    }

    /** unit: "month" o "quarter" (argumento de date_trunc). facilityIds null = todos. */
    public List<PeriodRow> trend(Long microorganismId, Long antibioticId, Long districtId,
                                 Collection<Long> facilityIds, LocalDate from, LocalDate to, String unit,
                                 DataOrigin origin) {
        MapSqlParameterSource params = params(microorganismId, districtId, facilityIds, from, to, origin)
                .addValue("antibioticId", antibioticId)
                .addValue("unit", unit);
        return jdbc.query(TREND, params, (rs, i) -> new PeriodRow(
                rs.getObject("period", LocalDate.class), rs.getLong("tested"),
                rs.getLong("susceptible"), rs.getLong("resistant")));
    }

    private static MapSqlParameterSource params(Long microorganismId, Long districtId, Collection<Long> facilityIds,
                                                LocalDate from, LocalDate to, DataOrigin origin) {
        boolean all = facilityIds == null;
        return new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to)
                .addValue("microorganismId", microorganismId, java.sql.Types.BIGINT)
                .addValue("districtId", districtId, java.sql.Types.BIGINT)
                .addValue("allFacilities", all)
                .addValue("specimen", null, java.sql.Types.VARCHAR)
                .addValue("origin", origin == null ? null : origin.name(), java.sql.Types.VARCHAR)
                .addValue("ward", null, java.sql.Types.VARCHAR)
                // "in ()" vacío es SQL inválido; con allFacilities = true esta lista no se evalúa.
                .addValue("facilityIds", all || facilityIds.isEmpty() ? List.of(-1L) : facilityIds);
    }

    private static Row mapRow(ResultSet rs, int i) throws SQLException {
        String species = rs.getString("species");
        String name = species == null ? rs.getString("genus") : rs.getString("genus") + " " + species;
        return new Row(rs.getLong("microorganism_id"), name, rs.getLong("antibiotic_id"),
                rs.getString("antibiotic_name"), rs.getLong("tested"), rs.getLong("susceptible"),
                rs.getLong("intermediate"), rs.getLong("resistant"), rs.getLong("identified"),
                rs.getLong("tested_all"), rs.getLong("real_count"));
    }
}
