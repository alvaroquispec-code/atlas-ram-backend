package pe.edu.utec.atlasrambackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.edu.utec.atlasrambackend.interpretation.MicParser;
import pe.edu.utec.atlasrambackend.interpretation.ParsedMic;
import pe.edu.utec.atlasrambackend.model.Antibiotic;
import pe.edu.utec.atlasrambackend.model.Breakpoint;
import pe.edu.utec.atlasrambackend.model.Interpretation;
import pe.edu.utec.atlasrambackend.model.InterpretationSource;
import pe.edu.utec.atlasrambackend.model.Microorganism;
import pe.edu.utec.atlasrambackend.repository.BreakpointRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterpretationServiceTest {

    /** CLSI 2024, E. coli frente a meropenem: S ≤ 1, I = 2, R ≥ 4. */
    private static Breakpoint meropenemEcoli() {
        Breakpoint bp = new Breakpoint();
        bp.setSusceptibleMax(new BigDecimal("1"));
        bp.setResistantMin(new BigDecimal("4"));
        return bp;
    }

    private static ParsedMic mic(String raw) {
        return MicParser.parse(raw).orElseThrow();
    }

    @ParameterizedTest(name = "CIM {0} → {1}")
    @CsvSource({
            "0.5,     SUSCEPTIBLE",
            "1,       SUSCEPTIBLE",
            "2,       INTERMEDIATE",
            "4,       RESISTANT",
            "<=0.25,  SUSCEPTIBLE",   // la real es ≤ 0.25 → todo el rango es S
            "<=1,     SUSCEPTIBLE",   // ≤ 1 sigue siendo todo S
    })
    void clasificaValoresExactosYRangosConcluyentes(String raw, Interpretation expected) {
        assertThat(InterpretationService.classify(mic(raw), meropenemEcoli())).contains(expected);
    }

    @Test
    void rangosHaciaArribaOAbajoQueCaenEnUnaCategoria() {
        assertThat(InterpretationService.classify(mic(">=4"), meropenemEcoli())).contains(Interpretation.RESISTANT);
        assertThat(InterpretationService.classify(mic(">=16"), meropenemEcoli())).contains(Interpretation.RESISTANT);
        // ">2" significa "≥ 4" en una serie de diluciones dobles → R
        assertThat(InterpretationService.classify(mic(">2"), meropenemEcoli())).contains(Interpretation.RESISTANT);
        // "<2" significa "≤ 1" → S
        assertThat(InterpretationService.classify(mic("<2"), meropenemEcoli())).contains(Interpretation.SUSCEPTIBLE);
    }

    @Test
    void noAdivinaCuandoElRangoCruzaCategorias() {
        // "≤ 4" puede ser 0.5 (S), 2 (I) o 4 (R): no hay una sola respuesta.
        assertThat(InterpretationService.classify(mic("<=4"), meropenemEcoli())).isEmpty();
        // "≥ 2" puede ser 2 (I) o 8 (R).
        assertThat(InterpretationService.classify(mic(">=2"), meropenemEcoli())).isEmpty();
    }

    private BreakpointRepository breakpointRepository;
    private InterpretationService service;
    private final Microorganism ecoli = new Microorganism();
    private final Antibiotic meropenem = new Antibiotic();

    @BeforeEach
    void setUp() {
        breakpointRepository = mock(BreakpointRepository.class);
        service = new InterpretationService(breakpointRepository, "CLSI", "2024");
        ecoli.setId(1L);
        meropenem.setId(11L);
    }

    @Test
    void conPuntoDeCorteLaInterpretacionEsCalculadaYSellada() {
        when(breakpointRepository.findByMicroorganismIdAndAntibioticIdAndStandardAndVersion(1L, 11L, "CLSI", "2024"))
                .thenReturn(Optional.of(meropenemEcoli()));

        var resolution = service.resolve(mic(">=16"), Interpretation.RESISTANT, ecoli, meropenem).orElseThrow();

        assertThat(resolution.interpretation()).isEqualTo(Interpretation.RESISTANT);
        assertThat(resolution.source()).isEqualTo(InterpretationSource.CALCULATED);
        assertThat(resolution.breakpointStandard()).isEqualTo("CLSI");
        assertThat(resolution.breakpointVersion()).isEqualTo("2024");
    }

    @Test
    void sinPuntoDeCorteSeUsaLaDelLaboratorioSinSelloClsi() {
        when(breakpointRepository.findByMicroorganismIdAndAntibioticIdAndStandardAndVersion(anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        var resolution = service.resolve(mic("2"), Interpretation.SUSCEPTIBLE, ecoli, meropenem).orElseThrow();

        assertThat(resolution.interpretation()).isEqualTo(Interpretation.SUSCEPTIBLE);
        assertThat(resolution.source()).isEqualTo(InterpretationSource.LAB_REPORTED);
        assertThat(resolution.breakpointStandard()).isNull();
        assertThat(resolution.breakpointVersion()).isNull();
    }

    @Test
    void rangoAmbiguoUsaLaDelLaboratorio() {
        when(breakpointRepository.findByMicroorganismIdAndAntibioticIdAndStandardAndVersion(1L, 11L, "CLSI", "2024"))
                .thenReturn(Optional.of(meropenemEcoli()));

        var resolution = service.resolve(mic("<=4"), Interpretation.INTERMEDIATE, ecoli, meropenem).orElseThrow();

        assertThat(resolution.source()).isEqualTo(InterpretationSource.LAB_REPORTED);
        assertThat(resolution.interpretation()).isEqualTo(Interpretation.INTERMEDIATE);
    }

    @Test
    void sinCalculoPosibleNiInterpretacionDelLaboratorioNoHayResultado() {
        when(breakpointRepository.findByMicroorganismIdAndAntibioticIdAndStandardAndVersion(1L, 11L, "CLSI", "2024"))
                .thenReturn(Optional.of(meropenemEcoli()));

        assertThat(service.resolve(mic("<=4"), null, ecoli, meropenem)).isEmpty();
        assertThat(service.resolve(null, null, ecoli, meropenem)).isEmpty();
    }
}
