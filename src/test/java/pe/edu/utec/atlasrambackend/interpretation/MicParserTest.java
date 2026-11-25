package pe.edu.utec.atlasrambackend.interpretation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import pe.edu.utec.atlasrambackend.model.MicQualifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MicParserTest {

    @ParameterizedTest(name = "\"{0}\" → {1} {2}")
    @CsvSource(delimiter = '|', value = {
            "2        | EQ | 2",
            "0.5      | EQ | 0.5",
            "'0,5'    | EQ | 0.5",
            ".25      | EQ | 0.25",
            "=2       | EQ | 2",
            "<=0.25   | LE | 0.25",
            "≤0.25    | LE | 0.25",
            ">=16     | GE | 16",
            "≥16      | GE | 16",
            ">32      | GT | 32",
            "<0.5     | LT | 0.5",
            "'> 32'   | GT | 32",
            "2/38     | EQ | 2",
            "<=20/380 | LE | 20"
    })
    void reconoceLosFormatosDeLosEquiposYWhonet(String raw, MicQualifier qualifier, String value) {
        ParsedMic mic = MicParser.parse(raw).orElseThrow();

        assertThat(mic.qualifier()).isEqualTo(qualifier);
        assertThat(mic.value()).isEqualByComparingTo(new BigDecimal(value));
    }

    @Test
    void textoVacioNoEsUnaCim() {
        assertThat(MicParser.parse("")).isEmpty();
        assertThat(MicParser.parse("   ")).isEmpty();
        assertThat(MicParser.parse(null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"R", "abc", "<=", "16 mg", "0", "<=0"})
    void rechazaValoresQueNoSonCim(String raw) {
        assertThatThrownBy(() -> MicParser.parse(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CIM");
    }
}
