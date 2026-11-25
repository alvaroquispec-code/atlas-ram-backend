package pe.edu.utec.atlasrambackend.service;

import org.junit.jupiter.api.Test;
import pe.edu.utec.atlasrambackend.model.Breakpoint;
import pe.edu.utec.atlasrambackend.model.Interpretation;
import pe.edu.utec.atlasrambackend.model.InterpretationSource;
import pe.edu.utec.atlasrambackend.model.MicQualifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ReinterpretationServiceTest {

    private static Breakpoint mic(double s, double r) {
        Breakpoint bp = new Breakpoint();
        bp.setSusceptibleMax(BigDecimal.valueOf(s));
        bp.setResistantMin(BigDecimal.valueOf(r));
        return bp;
    }

    private static ReinterpretationService.Row gentamicin(String mic, MicQualifier q, Interpretation current) {
        return new ReinterpretationService.Row(1L, 1L, 12L, "Escherichia coli", "Gentamicina",
                mic == null ? null : new BigDecimal(mic), q, null, current, Interpretation.RESISTANT,
                InterpretationSource.CALCULATED, "CLSI", "2024");
    }

    @Test
    void elCambioDeCLSI2023EnAminoglucosidosReclasificaLaMismaCim() {
        // Gentamicina en Enterobacterales: CLSI 2022 S ≤ 4, R ≥ 16; desde 2023 S ≤ 2, R ≥ 8.
        var row = gentamicin("8", MicQualifier.EQ, Interpretation.RESISTANT);

        var with2022 = ReinterpretationService.reinterpretRow(row, mic(4, 16), "CLSI", "2022").orElseThrow();

        assertThat(with2022.interpretation()).isEqualTo(Interpretation.INTERMEDIATE);
        assertThat(with2022.version()).isEqualTo("2022");
        assertThat(with2022.source()).isEqualTo(InterpretationSource.CALCULATED);
    }

    @Test
    void unaCimConCalificadorQueCruzaCategoriasUsaLaDelLaboratorio() {
        // "<=8" con S ≤ 2: la CIM real puede estar en cualquier categoría, no se adivina.
        var row = gentamicin("8", MicQualifier.LE, Interpretation.RESISTANT);

        var outcome = ReinterpretationService.reinterpretRow(row, mic(2, 8), "CLSI", "2024").orElseThrow();

        assertThat(outcome.source()).isEqualTo(InterpretationSource.LAB_REPORTED);
        assertThat(outcome.interpretation()).isEqualTo(Interpretation.RESISTANT);
        assertThat(outcome.version()).isNull();
    }

    @Test
    void elHaloSeUsaCuandoNoHayCim() {
        Breakpoint disk = new Breakpoint();
        disk.setDiskSusceptibleMin(15);
        disk.setDiskResistantMax(12);
        var row = new ReinterpretationService.Row(1L, 1L, 12L, "Escherichia coli", "Gentamicina", null, null, 18,
                Interpretation.RESISTANT, null, InterpretationSource.LAB_REPORTED, null, null);

        var outcome = ReinterpretationService.reinterpretRow(row, disk, "CLSI", "2024").orElseThrow();

        assertThat(outcome.interpretation()).isEqualTo(Interpretation.SUSCEPTIBLE);
        assertThat(outcome.source()).isEqualTo(InterpretationSource.CALCULATED);
    }
}
