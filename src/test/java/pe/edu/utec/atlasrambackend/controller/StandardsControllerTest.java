package pe.edu.utec.atlasrambackend.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StandardsControllerTest {

    @Test
    void laEdicionDeM100SeDerivaDelAnio() {
        assertThat(StandardsController.describeBreakpoints("CLSI", "2024")).isEqualTo("CLSI M100-Ed34 (2024)");
        assertThat(StandardsController.describeBreakpoints("CLSI", "2025")).isEqualTo("CLSI M100-Ed35 (2025)");
    }

    @Test
    void otroEstandarSeMuestraTalCual() {
        assertThat(StandardsController.describeBreakpoints("EUCAST", "15.0")).isEqualTo("EUCAST 15.0");
    }

    @Test
    void laRespuestaDistingueM100DeM39() {
        var body = new StandardsController("CLSI", "2024").standards().getBody();

        assertThat(body.breakpoints()).isEqualTo("CLSI M100-Ed34 (2024)");
        assertThat(body.aggregation()).startsWith("CLSI M39");
    }
}
