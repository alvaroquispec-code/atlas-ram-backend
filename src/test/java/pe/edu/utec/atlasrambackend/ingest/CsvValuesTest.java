package pe.edu.utec.atlasrambackend.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.edu.utec.atlasrambackend.model.Interpretation;
import pe.edu.utec.atlasrambackend.model.Specimen;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvValuesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 22);

    @Test
    void fechasIsoYPeruanas() {
        assertThat(CsvValues.parseDate("2026-08-20", TODAY)).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(CsvValues.parseDate("20/08/2026", TODAY)).isEqualTo(LocalDate.of(2026, 8, 20));
    }

    @Test
    void fechasInvalidasOFuturas() {
        assertThatThrownBy(() -> CsvValues.parseDate("2026-02-30", TODAY)).hasMessageContaining("inválida");
        assertThatThrownBy(() -> CsvValues.parseDate("08/20/2026", TODAY)).hasMessageContaining("inválida");
        assertThatThrownBy(() -> CsvValues.parseDate("2026-12-01", TODAY)).hasMessageContaining("futuro");
    }

    @ParameterizedTest
    @CsvSource({"URINE,URINE", "orina,URINE", "Sangre,BLOOD", "LCR,CSF",
            "Líquido cefalorraquídeo,CSF", "heces,STOOL", "Esputo,RESPIRATORY"})
    void tiposDeMuestraEnInglesYEspanol(String raw, Specimen expected) {
        assertThat(CsvValues.parseSpecimen(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"S,SUSCEPTIBLE", "sensible,SUSCEPTIBLE", "I,INTERMEDIATE", "R,RESISTANT", "Resistente,RESISTANT"})
    void interpretacionesAbreviadasYEnEspanol(String raw, Interpretation expected) {
        assertThat(CsvValues.parseInterpretation(raw)).isEqualTo(expected);
    }

    @Test
    void interpretacionVaciaEsNullYSddSeRechaza() {
        assertThat(CsvValues.parseInterpretation("  ")).isNull();
        assertThatThrownBy(() -> CsvValues.parseInterpretation("SDD")).hasMessageContaining("SDD");
    }

    @Test
    void edadYSexo() {
        assertThat(CsvValues.parseAge("47")).isEqualTo(47);
        assertThat(CsvValues.parseAge("")).isNull();
        assertThatThrownBy(() -> CsvValues.parseAge("150")).hasMessageContaining("rango");
        assertThatThrownBy(() -> CsvValues.parseAge("47 años")).hasMessageContaining("edad inválida");
        assertThat(CsvValues.parseSex("h")).isEqualTo("M");
        assertThat(CsvValues.parseSex("Femenino")).isEqualTo("F");
        assertThatThrownBy(() -> CsvValues.parseSex("X")).hasMessageContaining("sexo inválido");
    }

    @Test
    void elIdentificadorDePacienteSeNormaliza() {
        assertThat(CsvValues.normalizePatientId(" hc-0012.345 "))
                .isEqualTo(CsvValues.normalizePatientId("HC0012345"));
    }
}
