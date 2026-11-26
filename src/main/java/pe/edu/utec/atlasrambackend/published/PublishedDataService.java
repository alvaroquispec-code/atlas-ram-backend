package pe.edu.utec.atlasrambackend.published;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.model.DataOrigin;
import pe.edu.utec.atlasrambackend.repository.AntibiogramQueries;
import pe.edu.utec.atlasrambackend.stats.Proportions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Datos reales publicados (V17): agregados de fuentes abiertas, como VIRAPERU. Nunca se mezclan con los
 * aislamientos; se muestran aparte y junto al valor que calcula Atlas RAM, para que se vea qué tan cerca
 * están los datos de la plataforma de la realidad publicada.
 */
@Service
public class PublishedDataService {

    /** Ventana de Atlas RAM para la comparación: todos sus datos. */
    static final LocalDate ATLAS_FROM = LocalDate.of(2000, 1, 1);

    private final NamedParameterJdbcTemplate jdbc;
    private final AntibiogramQueries antibiogramQueries;

    public PublishedDataService(NamedParameterJdbcTemplate jdbc, AntibiogramQueries antibiogramQueries) {
        this.jdbc = jdbc;
        this.antibiogramQueries = antibiogramQueries;
    }

    private record Source(long id, String code, String title, String citation, String url, String license,
                          String setting, String specimenType, LocalDate periodStart, LocalDate periodEnd,
                          String breakpointStandard) {
    }

    private record Result(long sourceId, long microorganismId, long antibioticId, String organismLabel,
                          String antibioticName, int tested, int resistant) {
    }

    @Transactional(readOnly = true)
    public List<PublishedAntibiogramDTO> findAll(DataOrigin atlasOrigin) {
        List<Source> sources = jdbc.query("""
                select id, code, title, citation, url, license, setting, specimen_type, period_start, period_end,
                       breakpoint_standard
                from published_source order by id
                """, new MapSqlParameterSource(), (rs, i) -> new Source(rs.getLong("id"), rs.getString("code"), rs.getString("title"),
                rs.getString("citation"), rs.getString("url"), rs.getString("license"), rs.getString("setting"),
                rs.getString("specimen_type"), rs.getObject("period_start", LocalDate.class),
                rs.getObject("period_end", LocalDate.class), rs.getString("breakpoint_standard")));
        if (sources.isEmpty()) {
            return List.of();
        }
        List<Result> results = jdbc.query("""
                select r.source_id, r.microorganism_id, r.antibiotic_id, r.organism_label, a.name as antibiotic_name,
                       r.tested, r.resistant
                from published_result r
                join antibiotic a on a.id = r.antibiotic_id
                order by r.source_id, r.organism_label, a.name
                """, new MapSqlParameterSource(), (rs, i) -> new Result(rs.getLong("source_id"),
                rs.getLong("microorganism_id"), rs.getLong("antibiotic_id"), rs.getString("organism_label"),
                rs.getString("antibiotic_name"), rs.getInt("tested"), rs.getInt("resistant")));

        Map<String, Map<String, AntibiogramQueries.Row>> atlasBySpecimen = new HashMap<>();
        Map<Long, List<PublishedAntibiogramDTO.Row>> rowsBySource = new LinkedHashMap<>();
        for (Source s : sources) {
            rowsBySource.put(s.id(), new ArrayList<>());
            atlasBySpecimen.computeIfAbsent(s.specimenType(), specimen -> {
                Map<String, AntibiogramQueries.Row> bySpecies = new HashMap<>();
                antibiogramQueries.antibiogramBySpecimen(specimen, ATLAS_FROM, LocalDate.now(), atlasOrigin)
                        .forEach(r -> bySpecies.putIfAbsent(r.microorganismId() + ":" + r.antibioticId(), r));
                return bySpecies;
            });
        }
        Map<Long, Source> sourceById = new HashMap<>();
        sources.forEach(s -> sourceById.put(s.id(), s));
        for (Result r : results) {
            Source s = sourceById.get(r.sourceId());
            AntibiogramQueries.Row atlas = atlasBySpecimen.get(s.specimenType())
                    .get(r.microorganismId() + ":" + r.antibioticId());
            Proportions.Interval ci = Proportions.wilson(r.resistant(), r.tested());
            rowsBySource.get(s.id()).add(new PublishedAntibiogramDTO.Row(
                    r.microorganismId(), r.antibioticId(), r.organismLabel(), r.antibioticName(),
                    r.tested(), r.resistant(), Proportions.percentage(r.resistant(), r.tested()),
                    ci.low(), ci.high(),
                    atlas == null ? null : atlas.tested(),
                    atlas == null || atlas.tested() == 0 ? null
                            : Proportions.percentage(atlas.resistant(), atlas.tested())));
        }
        return sources.stream().map(s -> new PublishedAntibiogramDTO(s.code(), s.title(), s.citation(), s.url(),
                s.license(), s.setting(), s.specimenType(), s.periodStart(), s.periodEnd(), s.breakpointStandard(),
                rowsBySource.get(s.id()))).toList();
    }
}
