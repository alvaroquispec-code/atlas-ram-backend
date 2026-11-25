package pe.edu.utec.atlasrambackend.service;

import pe.edu.utec.atlasrambackend.dto.CsvRowDTO;
import pe.edu.utec.atlasrambackend.exception.InvalidCsvFormatException;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Exportación de WHONET en formato "ancho": una fila por aislamiento y una columna por antibiótico.
 * Se convierte al formato "largo" de Atlas RAM (una fila por aislamiento y antibiótico) y desde ahí sigue el
 * mismo camino que cualquier CSV: validación por fila, interpretación, seudonimización y primer aislamiento.
 *
 * <p>Columnas de antibiótico: código de 3 letras, guion bajo, una letra de la guía, una del método y, en los
 * discos, la carga: {@code MEM_NM}, {@code CIP_EE}, {@code AMP_ND10}. Método M (CIM) y E (gradiente, que
 * también da una CIM) → se lee como CIM; D (disco) → diámetro del halo en mm, que se interpreta con el
 * punto de corte de disco. Si un antibiótico trae CIM y disco se guardan ambos y la interpretación usa
 * primero la CIM. En cualquier columna se acepta también una interpretación ya hecha (S, I o R).</p>
 *
 * <p>Las columnas de datos del aislamiento se reconocen por varios nombres (técnicos, en inglés y en
 * español), porque cada laboratorio configura su exportación. Agregar un nombre nuevo es una línea en ALIASES.</p>
 */
final class WhonetFormat {

    private static final Pattern ANTIBIOTIC_COLUMN = Pattern.compile("^([A-Z]{3})_([A-Z])([DME])(\\d*)$");
    private static final Pattern DATE_WITH_TIME = Pattern.compile("^(\\S+)\\s+\\d{1,2}:\\d{2}(:\\d{2})?$");
    private static final Set<String> INTERPRETATIONS = Set.of(
            "S", "I", "R", "SUSCEPTIBLE", "INTERMEDIATE", "RESISTANT", "SENSIBLE", "INTERMEDIO", "RESISTENTE");

    enum Field { PATIENT_ID, SPECIMEN_NUMBER, ISOLATE_NUMBER, SPECIMEN_DATE, SPECIMEN_TYPE, ORGANISM, SEX, AGE, WARD }

    /** Nombres aceptados, ya normalizados (minúsculas, sin tildes ni signos). */
    static final Map<Field, Set<String>> ALIASES = Map.of(
            Field.PATIENT_ID, Set.of("patientid", "identificationnumber", "idpaciente", "numerodeidentificacion",
                    "identificacion", "historiaclinica"),
            Field.SPECIMEN_NUMBER, Set.of("specnum", "specimennumber", "numerodemuestra", "nummuestra"),
            Field.ISOLATE_NUMBER, Set.of("isolnum", "isolatenumber", "numerodeaislamiento", "numaislamiento"),
            Field.SPECIMEN_DATE, Set.of("specdate", "specimendate", "fechademuestra", "fechadelamuestra",
                    "fechamuestra", "fechadetoma"),
            Field.SPECIMEN_TYPE, Set.of("spectype", "specimentype", "tipodemuestra", "tipomuestra"),
            Field.ORGANISM, Set.of("organism", "organismo", "microorganismo"),
            Field.SEX, Set.of("sex", "sexo"),
            Field.AGE, Set.of("age", "edad"),
            Field.WARD, Set.of("loctype", "locationtype", "wardtype", "tipodeubicacion", "tipodeservicio",
                    "servicio"));

    private WhonetFormat() {
    }

    /** Un archivo es de WHONET si trae al menos una columna de antibiótico y una de organismo. */
    static boolean matches(List<String> header) {
        boolean antibiotic = header.stream().anyMatch(h -> ANTIBIOTIC_COLUMN.matcher(h.trim()).matches());
        boolean organism = header.stream().anyMatch(h -> ALIASES.get(Field.ORGANISM).contains(key(h)));
        return antibiotic && organism;
    }

    static CsvParser.Result convert(List<String> header, List<CsvParser.CsvRecord> records) {
        Map<Field, Integer> fields = new EnumMap<>(Field.class);
        Map<String, AntibioticColumns> antibiotics = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String name = header.get(i).trim();
            Matcher m = ANTIBIOTIC_COLUMN.matcher(name);
            if (m.matches()) {
                AntibioticColumns columns = antibiotics.computeIfAbsent(m.group(1), k -> new AntibioticColumns());
                (m.group(3).equals("D") ? columns.disk : columns.mic).add(new Column(i, name));
                continue;
            }
            for (Map.Entry<Field, Set<String>> alias : ALIASES.entrySet()) {
                if (alias.getValue().contains(key(name))) {
                    fields.putIfAbsent(alias.getKey(), i);
                }
            }
        }
        List<String> missing = new ArrayList<>();
        for (Field required : List.of(Field.SPECIMEN_DATE, Field.SPECIMEN_TYPE, Field.ORGANISM)) {
            if (!fields.containsKey(required)) {
                missing.add(required.name());
            }
        }
        if (!missing.isEmpty()) {
            throw new InvalidCsvFormatException("Archivo de WHONET sin las columnas " + String.join(", ", missing)
                    + ". Nombres aceptados: " + ALIASES.get(Field.SPECIMEN_DATE) + ", "
                    + ALIASES.get(Field.SPECIMEN_TYPE) + ", " + ALIASES.get(Field.ORGANISM));
        }

        int width = header.size();
        List<CsvRowDTO> rows = new ArrayList<>();
        List<String> malformed = new ArrayList<>();
        for (CsvParser.CsvRecord record : records) {
            if (record.fields().size() != width) {
                malformed.add("Línea " + record.line() + ": se esperaban " + width
                        + " columnas y llegaron " + record.fields().size());
                continue;
            }
            String date = stripTime(get(record, fields, Field.SPECIMEN_DATE));
            String specimen = get(record, fields, Field.SPECIMEN_TYPE);
            String organism = get(record, fields, Field.ORGANISM).toLowerCase(Locale.ROOT);
            String patient = get(record, fields, Field.PATIENT_ID);
            String specimenNumber = get(record, fields, Field.SPECIMEN_NUMBER);
            String isolateNumber = get(record, fields, Field.ISOLATE_NUMBER);
            String labIsolateId = specimenNumber.isEmpty() ? ""
                    : isolateNumber.isEmpty() ? specimenNumber : specimenNumber + "-" + isolateNumber;
            String age = get(record, fields, Field.AGE);
            String sex = get(record, fields, Field.SEX);
            String ward = get(record, fields, Field.WARD);

            for (Map.Entry<String, AntibioticColumns> entry : antibiotics.entrySet()) {
                String mic = entry.getValue().first(record, entry.getValue().mic);
                String disk = entry.getValue().first(record, entry.getValue().disk);
                if (mic.isEmpty() && disk.isEmpty()) {
                    continue;
                }
                // Una columna puede traer la interpretación en vez del número (S, I, R).
                String reported = "";
                if (isInterpretation(mic)) {
                    reported = mic;
                    mic = "";
                }
                if (isInterpretation(disk)) {
                    reported = reported.isEmpty() ? disk : reported;
                    disk = "";
                }
                rows.add(new CsvRowDTO(record.line(), date, specimen, age, sex, "", "", organism, entry.getKey(),
                        mic, reported, patient, labIsolateId, disk, ward));
            }
        }
        return new CsvParser.Result(rows, malformed);
    }

    private record Column(int index, String name) {
    }

    private static boolean isInterpretation(String value) {
        return INTERPRETATIONS.contains(value.toUpperCase(Locale.ROOT));
    }

    /** Las columnas de un mismo antibiótico, separadas por método (CIM y disco). */
    private static final class AntibioticColumns {
        final List<Column> mic = new ArrayList<>();
        final List<Column> disk = new ArrayList<>();

        /** El primer valor no vacío de esas columnas (o vacío). */
        String first(CsvParser.CsvRecord record, List<Column> columns) {
            for (Column c : columns) {
                String v = record.fields().get(c.index()).trim();
                if (!v.isEmpty()) {
                    return v;
                }
            }
            return "";
        }
    }

    private static String get(CsvParser.CsvRecord record, Map<Field, Integer> fields, Field field) {
        Integer index = fields.get(field);
        return index == null ? "" : record.fields().get(index).trim();
    }

    private static String stripTime(String value) {
        Matcher m = DATE_WITH_TIME.matcher(value);
        return m.matches() ? m.group(1) : value;
    }

    static String key(String header) {
        String plain = Normalizer.normalize(header.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
