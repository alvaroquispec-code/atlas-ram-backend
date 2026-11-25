package pe.edu.utec.atlasrambackend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utec.atlasrambackend.dto.StandardsResponseDTO;

/**
 * Qué normas usa el sistema. Público: quien consulta un antibiograma debe poder saber con qué edición de
 * puntos de corte se interpretó.
 *
 * CLSI M100 define los puntos de corte (S/I/R) y se reedita cada año; CLSI M39 define cómo se acumula el
 * antibiograma (primer aislamiento por paciente, mínimo de 30). Son complementarias, no versiones de lo mismo.
 */
@RestController
@RequestMapping("/standards")
public class StandardsController {

    static final String AGGREGATION = "CLSI M39: primer aislamiento por paciente y especie; mínimo de 30 aislamientos";
    static final String EXPECTED_PHENOTYPES =
            "EUCAST Expected Resistant Phenotypes v1.2 (2023) y Expected Susceptible Phenotypes v1.1 (2022)";

    private final String standard;
    private final String version;

    public StandardsController(@Value("${breakpoints.standard:CLSI}") String standard,
                               @Value("${breakpoints.version:2024}") String version) {
        this.standard = standard;
        this.version = version;
    }

    @GetMapping
    public ResponseEntity<StandardsResponseDTO> standards() {
        return ResponseEntity.ok(new StandardsResponseDTO(
                describeBreakpoints(standard, version), standard, version, AGGREGATION, EXPECTED_PHENOTYPES));
    }

    /**
     * "CLSI M100-Ed34 (2024)". La edición de M100 sigue al año de publicación desde hace décadas
     * (Ed32 = 2022, Ed34 = 2024, Ed35 = 2025), así que se deriva del año configurado.
     */
    static String describeBreakpoints(String standard, String version) {
        if ("CLSI".equalsIgnoreCase(standard) && version != null && version.matches("\\d{4}")) {
            int edition = Integer.parseInt(version) - 1990;
            return "CLSI M100-Ed" + edition + " (" + version + ")";
        }
        return standard + " " + version;
    }
}
