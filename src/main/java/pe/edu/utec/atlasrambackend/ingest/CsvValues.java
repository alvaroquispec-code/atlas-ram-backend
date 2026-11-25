package pe.edu.utec.atlasrambackend.ingest;

import pe.edu.utec.atlasrambackend.model.Interpretation;
import pe.edu.utec.atlasrambackend.model.Specimen;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Map;

/**
 * Convierte los valores de texto del CSV a tipos del dominio, con mensajes en español y aceptando
 * las formas habituales en laboratorios peruanos (fechas DD/MM/AAAA, "ORINA", "S/I/R", "F/M").
 */
public final class CsvValues {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter PERU = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    private static final Map<String, Specimen> SPECIMENS = Map.ofEntries(
            Map.entry("BLOOD", Specimen.BLOOD), Map.entry("SANGRE", Specimen.BLOOD),
            Map.entry("HEMOCULTIVO", Specimen.BLOOD),
            Map.entry("URINE", Specimen.URINE), Map.entry("ORINA", Specimen.URINE),
            Map.entry("UROCULTIVO", Specimen.URINE),
            Map.entry("RESPIRATORY", Specimen.RESPIRATORY), Map.entry("RESPIRATORIO", Specimen.RESPIRATORY),
            Map.entry("RESPIRATORIA", Specimen.RESPIRATORY), Map.entry("ESPUTO", Specimen.RESPIRATORY),
            Map.entry("WOUND", Specimen.WOUND), Map.entry("HERIDA", Specimen.WOUND),
            Map.entry("PIEL_Y_PARTES_BLANDAS", Specimen.WOUND),
            Map.entry("CSF", Specimen.CSF), Map.entry("LCR", Specimen.CSF),
            Map.entry("LIQUIDO_CEFALORRAQUIDEO", Specimen.CSF),
            Map.entry("STOOL", Specimen.STOOL), Map.entry("HECES", Specimen.STOOL),
            Map.entry("COPROCULTIVO", Specimen.STOOL),
            Map.entry("OTHER", Specimen.OTHER), Map.entry("OTRO", Specimen.OTHER), Map.entry("OTRA", Specimen.OTHER));

    private static final Map<String, Interpretation> INTERPRETATIONS = Map.of(
            "S", Interpretation.SUSCEPTIBLE, "SUSCEPTIBLE", Interpretation.SUSCEPTIBLE,
            "SENSIBLE", Interpretation.SUSCEPTIBLE,
            "I", Interpretation.INTERMEDIATE, "INTERMEDIATE", Interpretation.INTERMEDIATE,
            "INTERMEDIO", Interpretation.INTERMEDIATE,
            "R", Interpretation.RESISTANT, "RESISTANT", Interpretation.RESISTANT,
            "RESISTENTE", Interpretation.RESISTANT);

    private CsvValues() {
    }

    /** AAAA-MM-DD o DD/MM/AAAA (la de Excel en español). No admite fechas futuras. */
    public static LocalDate parseDate(String raw, LocalDate today) {
        String v = raw.trim();
        LocalDate date;
        try {
            date = v.contains("/") ? LocalDate.parse(v, PERU) : LocalDate.parse(v, ISO);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "fecha_toma inválida: '" + raw + "' (use AAAA-MM-DD o DD/MM/AAAA)");
        }
        if (date.isAfter(today)) {
            throw new IllegalArgumentException("fecha_toma está en el futuro: " + date);
        }
        return date;
    }

    public static Specimen parseSpecimen(String raw) {
        Specimen specimen = SPECIMENS.get(normalize(raw));
        if (specimen == null) {
            throw new IllegalArgumentException("tipo_muestra desconocido: '" + raw
                    + "' (BLOOD/SANGRE, URINE/ORINA, RESPIRATORY, WOUND, CSF/LCR, STOOL/HECES, OTHER)");
        }
        return specimen;
    }

    /** Vacío = el laboratorio no informó interpretación. */
    public static Interpretation parseInterpretation(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = normalize(raw);
        if (key.equals("SDD")) {
            throw new IllegalArgumentException(
                    "interpretación SDD (sensible dependiente de la dosis) aún no soportada; informe la CIM");
        }
        Interpretation interpretation = INTERPRETATIONS.get(key);
        if (interpretation == null) {
            throw new IllegalArgumentException("interpretacion_laboratorio desconocida: '" + raw
                    + "' (S, I, R o SUSCEPTIBLE, INTERMEDIATE, RESISTANT)");
        }
        return interpretation;
    }

    /** Edad en años cumplidos, de 0 a 120. Vacío = no informada. */
    public static Integer parseAge(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int age;
        try {
            age = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("edad inválida: '" + raw + "' (años enteros)");
        }
        if (age < 0 || age > 120) {
            throw new IllegalArgumentException("edad fuera de rango: " + age);
        }
        return age;
    }

    /** M o F. Acepta H (hombre), MASCULINO, FEMENINO. Vacío = no informado. */
    public static String parseSex(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (normalize(raw)) {
            case "M", "H", "MASCULINO", "HOMBRE" -> "M";
            case "F", "FEMENINO", "MUJER" -> "F";
            default -> throw new IllegalArgumentException("sexo inválido: '" + raw + "' (M o F)");
        };
    }

    /**
     * Identificador de paciente normalizado para que "0012-345", "0012 345" y "0012345" den la misma
     * clave: sin espacios, guiones ni puntos, en mayúsculas.
     */
    public static String normalizePatientId(String raw) {
        return raw == null ? "" : raw.replaceAll("[\\s.\\-]", "").toUpperCase(Locale.ROOT);
    }

    /** Mayúsculas, sin tildes, espacios como guion bajo: "Líquido cefalorraquídeo" → LIQUIDO_CEFALORRAQUIDEO. */
    static String normalize(String raw) {
        String withoutAccents = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents.toUpperCase(Locale.ROOT).replaceAll("\\s+", "_");
    }
}
