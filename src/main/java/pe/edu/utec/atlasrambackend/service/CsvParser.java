package pe.edu.utec.atlasrambackend.service;

import org.springframework.stereotype.Component;
import pe.edu.utec.atlasrambackend.dto.CsvRowDTO;
import pe.edu.utec.atlasrambackend.exception.InvalidCsvFormatException;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lector de CSV según RFC 4180: campos entre comillas con comas o saltos de línea dentro, comillas
 * escapadas como "", fin de línea CRLF o LF. Además acepta lo que produce Excel en una configuración
 * regional peruana: separador punto y coma y marca BOM al inicio.
 *
 * Las columnas se reconocen por su nombre, no por su posición, y las que el sistema no usa se ignoran
 * (una exportación de laboratorio suele traer muchas más). Un error en la cabecera invalida el archivo;
 * un error en una fila solo descarta esa fila.
 *
 * También reconoce una exportación de WHONET (una columna por antibiótico) y la convierte: ver WhonetFormat.
 */
@Component
public class CsvParser {

    static final List<String> REQUIRED = List.of(
            "fecha_toma", "tipo_muestra", "codigo_microorganismo", "codigo_antibiotico");

    static final List<String> OPTIONAL = List.of(
            "codigo_establecimiento", "edad", "sexo", "ubigeo_residencia", "cim",
            "interpretacion_laboratorio", "id_paciente", "id_aislamiento", "halo_mm", "servicio");

    /** Otros nombres con que los laboratorios llaman al diámetro del halo. */
    static final Set<String> DISK_ALIASES = Set.of("halo", "diametro_halo", "diámetro_halo", "disco_mm", "diametro_mm");

    /** Otros nombres de la columna del servicio del paciente. */
    static final Set<String> WARD_ALIASES = Set.of("tipo_servicio", "servicio_paciente", "ubicacion", "ward",
            "location_type");

    /** Filas leídas y filas descartadas por estar mal formadas ("Línea N: motivo"). */
    public record Result(List<CsvRowDTO> rows, List<String> malformed) {
        public int totalRows() {
            return rows.size() + malformed.size();
        }
    }

    /** Un registro del archivo con la línea física donde empieza. */
    record CsvRecord(int line, List<String> fields) {
        boolean isBlank() {
            return fields.stream().allMatch(String::isBlank);
        }
    }

    public Result parse(byte[] content) {
        List<CsvRecord> records = readRecords(content);

        CsvRecord header = records.get(0);
        if (WhonetFormat.matches(header.fields())) {
            return WhonetFormat.convert(header.fields(), records.subList(1, records.size()));
        }
        Map<String, Integer> columns = mapHeader(header.fields());
        int width = header.fields().size();

        List<CsvRowDTO> rows = new ArrayList<>();
        List<String> malformed = new ArrayList<>();
        for (CsvRecord record : records.subList(1, records.size())) {
            if (record.fields().size() != width) {
                malformed.add("Línea " + record.line() + ": se esperaban " + width
                        + " columnas y llegaron " + record.fields().size());
                continue;
            }
            rows.add(toRow(record, columns));
        }
        return new Result(rows, malformed);
    }

    private static CsvRowDTO toRow(CsvRecord r, Map<String, Integer> columns) {
        return new CsvRowDTO(
                r.line(),
                value(r, columns, "fecha_toma"),
                value(r, columns, "tipo_muestra"),
                value(r, columns, "edad"),
                value(r, columns, "sexo"),
                value(r, columns, "codigo_establecimiento"),
                value(r, columns, "ubigeo_residencia"),
                value(r, columns, "codigo_microorganismo"),
                value(r, columns, "codigo_antibiotico"),
                value(r, columns, "cim"),
                value(r, columns, "interpretacion_laboratorio"),
                value(r, columns, "id_paciente"),
                value(r, columns, "id_aislamiento"),
                value(r, columns, "halo_mm"),
                value(r, columns, "servicio"));
    }

    private static String value(CsvRecord r, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        return index == null ? "" : r.fields().get(index).trim();
    }

    static Map<String, Integer> mapHeader(List<String> header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String name = header.get(i).trim().toLowerCase(Locale.ROOT);
            if (name.isEmpty()) {
                continue;
            }
            name = DISK_ALIASES.contains(name) ? "halo_mm" : WARD_ALIASES.contains(name) ? "servicio" : name;
            if (columns.putIfAbsent(name, i) != null) {
                throw new InvalidCsvFormatException("La columna '" + name + "' está repetida en la cabecera");
            }
        }
        Set<String> missing = new LinkedHashSet<>(REQUIRED);
        missing.removeAll(columns.keySet());
        if (!missing.isEmpty()) {
            throw new InvalidCsvFormatException("Faltan columnas obligatorias: " + String.join(", ", missing)
                    + ". Opcionales: " + String.join(", ", OPTIONAL));
        }
        return columns;
    }

    /** El separador más frecuente de la primera línea: coma, punto y coma (Excel en español) o tabulador. */
    static char detectDelimiter(String text) {
        int end = text.indexOf('\n');
        String first = end < 0 ? text : text.substring(0, end);
        long commas = first.chars().filter(c -> c == ',').count();
        long semicolons = first.chars().filter(c -> c == ';').count();
        long tabs = first.chars().filter(c -> c == '\t').count();
        if (tabs > commas && tabs > semicolons) {
            return '\t';
        }
        return semicolons > commas ? ';' : ',';
    }

    /** Máquina de estados de RFC 4180. */
    static List<CsvRecord> tokenize(String text, char delimiter) {
        List<CsvRecord> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean quotedField = false;
        int line = 1;
        int recordLine = 1;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    field.append(c);
                }
            } else if (c == '"' && field.isEmpty() && !quotedField) {
                inQuotes = true;
                quotedField = true;
            } else if (c == delimiter) {
                fields.add(field.toString());
                field.setLength(0);
                quotedField = false;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                fields.add(field.toString());
                records.add(new CsvRecord(recordLine, fields));
                fields = new ArrayList<>();
                field.setLength(0);
                quotedField = false;
                line++;
                recordLine = line;
            } else {
                field.append(c);
            }
        }
        if (inQuotes) {
            throw new InvalidCsvFormatException("Línea " + recordLine + ": hay comillas sin cerrar");
        }
        if (!field.isEmpty() || !fields.isEmpty() || quotedField) {
            fields.add(field.toString());
            records.add(new CsvRecord(recordLine, fields));
        }
        return records;
    }

    /** Registros no vacíos del archivo (UTF-8, sin BOM, separador detectado). Lo usa también el kardex. */
    static List<CsvRecord> readRecords(byte[] content) {
        String text = decode(content);
        if (!text.isEmpty() && text.charAt(0) == '\ufeff') {
            text = text.substring(1);
        }
        List<CsvRecord> records = tokenize(text, detectDelimiter(text));
        records.removeIf(CsvRecord::isBlank);
        if (records.isEmpty()) {
            throw new InvalidCsvFormatException("El archivo está vacío");
        }
        return records;
    }

    /** UTF-8 estricto: un archivo guardado como ANSI/Latin-1 da un error claro en vez de caracteres rotos. */
    private static String decode(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new InvalidCsvFormatException(
                    "El archivo no está en UTF-8. En Excel: Guardar como → \"CSV UTF-8 (delimitado por comas)\"");
        }
    }
}
