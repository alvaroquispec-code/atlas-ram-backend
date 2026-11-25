package pe.edu.utec.atlasrambackend.model;

/**
 * Calificador de una CIM. Los paneles automatizados prueban un rango finito de diluciones: si la
 * bacteria crece en todas, el equipo informa "mayor o igual que la más alta" (">=16"); si no crece
 * en ninguna, "menor o igual que la más baja" ("<=0.25").
 */
public enum MicQualifier {
    LT("<"),
    LE("<="),
    EQ("="),
    GE(">="),
    GT(">");

    private final String symbol;

    MicQualifier(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
