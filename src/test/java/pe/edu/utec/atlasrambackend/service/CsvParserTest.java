package pe.edu.utec.atlasrambackend.service;

import org.junit.jupiter.api.Test;
import pe.edu.utec.atlasrambackend.dto.CsvRowDTO;
import pe.edu.utec.atlasrambackend.exception.InvalidCsvFormatException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvParserTest {

    private static final String HEADER =
            "fecha_toma,tipo_muestra,codigo_microorganismo,codigo_antibiotico,cim,interpretacion_laboratorio";

    private final CsvParser parser = new CsvParser();

    private CsvParser.Result parse(String text) {
        return parser.parse(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void leeUnArchivoSencillo() {
        CsvParser.Result r = parse(HEADER + "\n2026-08-20,URINE,eco,MEM,<=0.25,S\n");

        assertThat(r.rows()).hasSize(1);
        CsvRowDTO row = r.rows().get(0);
        assertThat(row.lineNumber()).isEqualTo(2);
        assertThat(row.micValue()).isEqualTo("<=0.25");
        assertThat(row.patientId()).isEmpty();   // columna opcional ausente
    }

    @Test
    void unaComaDentroDeComillasNoPartElCampo() {
        // Con split(",") esta fila tenía 7 columnas y se rechazaba.
        CsvParser.Result r = parse(HEADER + ",id_paciente\n2026-08-20,URINE,eco,MEM,<=0.25,S,\"HC 12,345\"\n");

        assertThat(r.malformed()).isEmpty();
        assertThat(r.rows().get(0).patientId()).isEqualTo("HC 12,345");
    }

    @Test
    void comillasEscapadasYSaltoDeLineaDentroDeUnCampo() {
        CsvParser.Result r = parse(HEADER + ",id_aislamiento\n"
                + "2026-08-20,URINE,eco,MEM,,S,\"M-01 \"\"bis\"\"\nrepetido\"\n"
                + "2026-08-21,URINE,eco,CRO,,R,M-02\n");

        assertThat(r.rows()).hasSize(2);
        assertThat(r.rows().get(0).labIsolateId()).isEqualTo("M-01 \"bis\"\nrepetido");
        // La segunda fila empieza en la línea física 4 del archivo.
        assertThat(r.rows().get(1).lineNumber()).isEqualTo(4);
    }

    @Test
    void aceptaElFormatoDeExcelEnEspanol() {
        // Excel con configuración regional peruana: BOM, punto y coma, CRLF y coma decimal.
        String text = "﻿" + HEADER.replace(',', ';') + "\r\n2026-08-20;ORINA;eco;CIP;0,5;S\r\n";

        CsvParser.Result r = parse(text);

        assertThat(r.rows()).hasSize(1);
        assertThat(r.rows().get(0).micValue()).isEqualTo("0,5");
        assertThat(r.rows().get(0).specimenType()).isEqualTo("ORINA");
    }

    @Test
    void lasColumnasSeReconocenPorNombreEnCualquierOrdenYSeIgnoranLasDesconocidas() {
        CsvParser.Result r = parse("codigo_antibiotico,COLUMNA_EXTRA,fecha_toma,codigo_microorganismo,tipo_muestra\n"
                + "MEM,xyz,2026-08-20,eco,URINE\n");

        CsvRowDTO row = r.rows().get(0);
        assertThat(row.antibioticCode()).isEqualTo("MEM");
        assertThat(row.collectionDate()).isEqualTo("2026-08-20");
        assertThat(row.specimenType()).isEqualTo("URINE");
    }

    @Test
    void unaFilaConColumnasDeMasSeDescartaSinTumbarElArchivo() {
        CsvParser.Result r = parse(HEADER + "\n2026-08-20,URINE,eco,MEM,1,S,sobra\n2026-08-20,URINE,eco,CRO,1,S\n");

        assertThat(r.rows()).hasSize(1);
        assertThat(r.malformed()).containsExactly("Línea 2: se esperaban 6 columnas y llegaron 7");
        assertThat(r.totalRows()).isEqualTo(2);
    }

    @Test
    void faltaUnaColumnaObligatoria() {
        assertThatThrownBy(() -> parse("fecha_toma,tipo_muestra,codigo_antibiotico\n2026-08-20,URINE,MEM\n"))
                .isInstanceOf(InvalidCsvFormatException.class)
                .hasMessageContaining("codigo_microorganismo");
    }

    @Test
    void comillasSinCerrar() {
        assertThatThrownBy(() -> parse(HEADER + "\n2026-08-20,URINE,eco,MEM,\"1,S\n"))
                .isInstanceOf(InvalidCsvFormatException.class)
                .hasMessageContaining("Línea 2");
    }

    @Test
    void archivoQueNoEstaEnUtf8() {
        byte[] latin1 = (HEADER + "\n2026-08-20,ORINA,eco,MEM,1,S,").getBytes(StandardCharsets.ISO_8859_1);
        byte[] withAccent = new byte[latin1.length + 1];
        System.arraycopy(latin1, 0, withAccent, 0, latin1.length);
        withAccent[latin1.length] = (byte) 0xE9;   // "é" en Latin-1: secuencia UTF-8 inválida

        assertThatThrownBy(() -> parser.parse(withAccent))
                .isInstanceOf(InvalidCsvFormatException.class)
                .hasMessageContaining("UTF-8");
    }

    @Test
    void archivoVacioOSoloConLineasEnBlanco() {
        assertThatThrownBy(() -> parse("\n\n  \n"))
                .isInstanceOf(InvalidCsvFormatException.class)
                .hasMessageContaining("vacío");
    }
}
