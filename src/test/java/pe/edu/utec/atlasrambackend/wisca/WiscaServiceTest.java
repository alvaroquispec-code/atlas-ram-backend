package pe.edu.utec.atlasrambackend.wisca;

import org.junit.jupiter.api.Test;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.repository.AntibiogramQueries;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WiscaServiceTest {

    @Test
    void regimenesDesdeLaUrl() {
        // "TZP+AMK" en una URL llega como "TZP AMK"; los repetidos se eliminan.
        assertThat(WiscaService.parseRegimens(List.of("mem", "TZP AMK", "TZP+AMK", " ")))
                .containsExactly(List.of("MEM"), List.of("TZP", "AMK"));
    }

    @Test
    void limitesDeLosRegimenes() {
        assertThatThrownBy(() -> WiscaService.parseRegimens(List.of("A+B+C+D")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("máximo 3");
        assertThatThrownBy(() -> WiscaService.parseRegimens(List.of("")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void lasFilasSeAgrupanPorAislamiento() {
        List<AntibiogramQueries.ProfileRow> rows = List.of(
                new AntibiogramQueries.ProfileRow(10L, 1L, "eco", "Escherichia coli", "MEM", "SUSCEPTIBLE"),
                new AntibiogramQueries.ProfileRow(10L, 1L, "eco", "Escherichia coli", "CRO", "RESISTANT"),
                new AntibiogramQueries.ProfileRow(11L, 2L, "kpn", "Klebsiella pneumoniae", "MEM", "RESISTANT"));

        List<WiscaCalculator.IsolateProfile> profiles = WiscaService.toProfiles(rows);

        assertThat(profiles).hasSize(2);
        assertThat(profiles.get(0).results()).hasSize(2);
    }
}
