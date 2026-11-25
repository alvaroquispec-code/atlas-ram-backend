package pe.edu.utec.atlasrambackend.logistics;

/**
 * Situación de disponibilidad según los indicadores del SISMED (DIGEMID), a partir de los meses de
 * existencia disponible (MED = stock disponible ÷ CPMA).
 */
public enum StockState {
    /** Stock 0 con consumo en los últimos meses. */
    STOCKOUT("Desabastecido"),
    /** 0 < MED < 2. */
    UNDERSTOCK("Substock"),
    /** 2 ≤ MED ≤ 6. */
    NORMAL_STOCK("Normostock"),
    /** MED > 6. */
    OVERSTOCK("Sobrestock"),
    /** Hay stock pero ningún consumo en los últimos 6 meses (CPMA = 0). */
    NO_ROTATION("Sin rotación"),
    /** Sin stock y sin consumo: el ítem no tiene movimiento que evaluar. */
    NO_MOVEMENT("Sin movimiento");

    private final String label;

    StockState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
