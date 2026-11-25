package pe.edu.utec.atlasrambackend.interpretation;

import pe.edu.utec.atlasrambackend.model.MicQualifier;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte el texto de una CIM, tal como lo exportan los equipos y WHONET, en calificador y valor.
 *
 * Acepta: "2", "0.5", "0,5" (coma decimal), "<=0.25", "≤0.25", ">=16", "≥16", ">32", "<0.5", "=2",
 * "> 32" (con espacios) y "2/38" (combinaciones como trimetoprima/sulfametoxazol, donde por convención
 * la CIM es la del primer componente).
 */
public final class MicParser {

    private static final Pattern MIC = Pattern.compile("^(<=|>=|≤|≥|<|>|=)?\\s*(\\d+(?:[.,]\\d+)?|[.,]\\d+)(?:\\s*/\\s*\\d+(?:[.,]\\d+)?)?$");

    private MicParser() {
    }

    /** Vacío si el texto está en blanco; excepción con un mensaje claro si no es una CIM válida. */
    public static Optional<ParsedMic> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        Matcher m = MIC.matcher(raw.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("CIM no reconocida: '" + raw.trim() + "'");
        }
        BigDecimal value = new BigDecimal(m.group(2).replace(',', '.'));
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("La CIM debe ser mayor que cero: '" + raw.trim() + "'");
        }
        return Optional.of(new ParsedMic(qualifierOf(m.group(1)), value));
    }

    private static MicQualifier qualifierOf(String symbol) {
        if (symbol == null) {
            return MicQualifier.EQ;
        }
        return switch (symbol) {
            case "<" -> MicQualifier.LT;
            case "<=", "≤" -> MicQualifier.LE;
            case ">=", "≥" -> MicQualifier.GE;
            case ">" -> MicQualifier.GT;
            default -> MicQualifier.EQ;
        };
    }
}
