package pe.edu.utec.atlasrambackend.published;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pe.edu.utec.atlasrambackend.repository.AntibiogramQueries;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublishedDataServiceTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final AntibiogramQueries queries = mock(AntibiogramQueries.class);
    private final PublishedDataService service = new PublishedDataService(jdbc, queries);

    @Test
    @SuppressWarnings("unchecked")
    void devuelveLaFuenteConSuIntervaloYElValorDeAtlasParaElMismoPar() throws Exception {
        ResultSet source = mock(ResultSet.class);
        when(source.getLong("id")).thenReturn(1L);
        when(source.getString("code")).thenReturn("VIRAPERU-2023");
        when(source.getString("specimen_type")).thenReturn("BLOOD");
        when(source.getObject("period_start", LocalDate.class)).thenReturn(LocalDate.of(2017, 7, 1));
        ResultSet result = mock(ResultSet.class);
        when(result.getLong("source_id")).thenReturn(1L);
        when(result.getLong("microorganism_id")).thenReturn(2L);
        when(result.getLong("antibiotic_id")).thenReturn(11L);
        when(result.getString("organism_label")).thenReturn("Klebsiella pneumoniae");
        when(result.getString("antibiotic_name")).thenReturn("Meropenem");
        when(result.getInt("tested")).thenReturn(118);
        when(result.getInt("resistant")).thenReturn(11);

        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            RowMapper<?> mapper = inv.getArgument(2);
            return List.of(mapper.mapRow(sql.contains("from published_source") ? source : result, 0));
        });
        when(queries.antibiogramBySpecimen(eq("BLOOD"), any(), any(), isNull())).thenReturn(List.of(
                new AntibiogramQueries.Row(2L, "Klebsiella pneumoniae", 11L, "Meropenem", 200, 170, 0, 30, 0, 200, 0)));

        List<PublishedAntibiogramDTO> all = service.findAll(null);

        assertThat(all).hasSize(1);
        PublishedAntibiogramDTO.Row row = all.get(0).rows().get(0);
        assertThat(row.resistancePercentage()).isCloseTo(9.32, within(0.01));
        assertThat(row.resistanceCiLow()).isLessThan(row.resistancePercentage());
        assertThat(row.atlasTested()).isEqualTo(200L);
        assertThat(row.atlasResistancePercentage()).isCloseTo(15.0, within(1e-9));
    }
}
