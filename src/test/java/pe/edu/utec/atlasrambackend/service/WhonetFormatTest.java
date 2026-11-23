package pe.edu.utec.atlasrambackend.service;

import org.junit.jupiter.api.Test;
import pe.edu.utec.atlasrambackend.dto.CsvRowDTO;
import pe.edu.utec.atlasrambackend.exception.InvalidCsvFormatException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhonetFormatTest {

    private static CsvParser.Result parse(String csv) {
        return new CsvParser().parse(csv.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void unaFilaPorAislamientoSeConvierteEnUnaFilaPorAntibiotico() {
        CsvParser.Result r = parse("""
                PATIENT_ID,SPEC_NUM,SPEC_DATE,SPEC_TYPE,ORGANISM,SEX,AGE,MEM_NM,CIP_NM,AMK_NM
                HC-001,M-77,2026-08-20,URINE,eco,F,47,<=0.25,>=4,
                """);

        assertThat(r.malformed()).isEmpty();
        assertThat(r.rows()).extracting(CsvRowDTO::antibioticCode).containsExactly("MEM", "CIP");
        CsvRowDTO mem = r.rows().get(0);
        assertThat(mem.micValue()).isEqualTo("<=0.25");
        assertThat(mem.microorganismCode()).isEqualTo("eco");
        assertThat(mem.patientId()).isEqualTo("HC-001");
        assertThat(mem.labIsolateId()).isEqualTo("M-77");
        assertThat(mem.collectionDate()).isEqualTo("2026-08-20");
    }

    @Test
    void nombresLegiblesEnEspanolYFechaConHora() {
        CsvParser.Result r = parse("""
                Número de identificación;Número de muestra;Número de aislamiento;Fecha de la muestra;Tipo de muestra;Organismo;MEM_EE
                HC-002;M-80;2;20/08/2026 00:00;SANGRE;KPN;R
                """);

        CsvRowDTO row = r.rows().get(0);
        assertThat(row.collectionDate()).isEqualTo("20/08/2026");
        assertThat(row.labIsolateId()).isEqualTo("M-80-2");
        assertThat(row.microorganismCode()).isEqualTo("kpn");
        assertThat(row.reportedInterpretation()).isEqualTo("R");
        assertThat(row.micValue()).isEmpty();
    }

    @Test
    void conCimYDiscoMandaLaCim() {
        CsvParser.Result r = parse("""
                SPEC_DATE,SPEC_TYPE,ORGANISM,CIP_ND5,CIP_NM
                2026-08-20,URINE,eco,22,0.5
                """);

        assertThat(r.rows()).hasSize(1);
        assertThat(r.rows().get(0).micValue()).isEqualTo("0.5");
    }

    @Test
    void unDiametroDeDiscoSinInterpretarSeInformaComoError() {
        CsvParser.Result r = parse("""
                SPEC_DATE,SPEC_TYPE,ORGANISM,CIP_ND5,AMK_ND30
                2026-08-20,URINE,eco,22,S
                """);

        assertThat(r.rows()).extracting(CsvRowDTO::antibioticCode).containsExactly("AMK");
        assertThat(r.malformed()).hasSize(1);
        assertThat(r.malformed().get(0)).contains("CIP_ND5").contains("disco");
    }

    @Test
    void faltaUnaColumnaObligatoria() {
        assertThatThrownBy(() -> parse("""
                ORGANISM,MEM_NM
                eco,1
                """))
                .isInstanceOf(InvalidCsvFormatException.class)
                .hasMessageContaining("SPECIMEN_DATE");
    }

    @Test
    void elCsvPropioDeAtlasRamNoSeConfundeConWhonet() {
        CsvParser.Result r = parse("""
                fecha_toma,tipo_muestra,codigo_microorganismo,codigo_antibiotico,cim
                2026-08-20,URINE,eco,MEM,0.25
                """);

        assertThat(r.rows()).hasSize(1);
        assertThat(r.rows().get(0).antibioticCode()).isEqualTo("MEM");
    }
}
