package pe.edu.utec.atlasrambackend.service;

import org.springframework.stereotype.Component;
import pe.edu.utec.atlasrambackend.dto.CsvRowDTO;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class CsvParser {

    private static final String[] EXPECTED_HEADERS = {
            "fecha_toma", "tipo_muestra", "edad", "sexo",
            "codigo_establecimiento", "ubigeo_residencia",
            "codigo_microorganismo", "codigo_antibiotico",
            "cim", "interpretacion_laboratorio"
    };

    public List<CsvRowDTO> parse(InputStream input) throws IOException {
        List<CsvRowDTO> rows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("El archivo está vacío");
            }
            validateHeaders(headerLine);

            String line;
            int lineNumber = 1;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }

                String[] f = line.split(",", -1);
                if (f.length < EXPECTED_HEADERS.length) {
                    throw new IllegalArgumentException(
                            "Línea " + lineNumber + ": se esperaban "
                                    + EXPECTED_HEADERS.length + " columnas, llegaron " + f.length);
                }

                rows.add(new CsvRowDTO(
                        lineNumber,
                        clean(f[0]), clean(f[1]), clean(f[2]), clean(f[3]),
                        clean(f[4]), clean(f[5]), clean(f[6]), clean(f[7]),
                        clean(f[8]), clean(f[9])
                ));
            }
        }

        return rows;
    }

    private void validateHeaders(String headerLine) {
        String[] headers = headerLine.split(",", -1);
        if (headers.length < EXPECTED_HEADERS.length) {
            throw new IllegalArgumentException(
                    "La cabecera no tiene las columnas esperadas: "
                            + String.join(", ", EXPECTED_HEADERS));
        }
        for (int i = 0; i < EXPECTED_HEADERS.length; i++) {
            String actual = clean(headers[i]).toLowerCase();
            if (!actual.equals(EXPECTED_HEADERS[i])) {
                throw new IllegalArgumentException(
                        "Columna " + (i + 1) + ": se esperaba '" + EXPECTED_HEADERS[i]
                                + "' y llegó '" + actual + "'");
            }
        }
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
            v = v.substring(1, v.length() - 1);
        }
        return v;
    }
}

