package pe.edu.utec.atlasrambackend.logistics;

/**
 * Indicadores de disponibilidad del SISMED, tal como los usa DIGEMID:
 * <ul>
 *   <li>CPMA (consumo promedio mensual ajustado) = consumo de los últimos 6 meses ÷ número de esos meses
 *       CON consumo. Excluye los meses en cero porque se asume que fueron meses sin stock.</li>
 *   <li>MED (meses de existencia disponible) = stock disponible ÷ CPMA.</li>
 *   <li>Estado: desabastecido (stock 0), substock (MED &lt; 2), normostock (2 a 6), sobrestock (&gt; 6),
 *       sin rotación (hay stock y CPMA = 0).</li>
 * </ul>
 * El CPMA sobreestima el consumo de los ítems intermitentes (un mes en cero puede ser falta de demanda,
 * no de stock). Por eso la planificación usa además una tasa que sí incluye los meses en cero.
 */
public final class SismedIndicators {

    public static final int WINDOW_MONTHS = 6;
    static final double UNDERSTOCK_BELOW = 2.0;
    static final double OVERSTOCK_ABOVE = 6.0;

    private SismedIndicators() {
    }

    public record Result(double cpma, Double monthsOfStock, StockState state) {
    }

    /**
     * @param lastMonths consumo de los últimos meses completos (se usan los 6 más recientes, al final del arreglo)
     * @param availableStock unidades disponibles no vencidas
     */
    public static Result compute(int[] lastMonths, int availableStock) {
        if (availableStock < 0) {
            throw new IllegalArgumentException("El stock no puede ser negativo");
        }
        int from = Math.max(0, lastMonths.length - WINDOW_MONTHS);
        long total = 0;
        int monthsWithConsumption = 0;
        for (int i = from; i < lastMonths.length; i++) {
            if (lastMonths[i] > 0) {
                total += lastMonths[i];
                monthsWithConsumption++;
            }
        }
        double cpma = monthsWithConsumption == 0 ? 0 : (double) total / monthsWithConsumption;

        if (cpma == 0) {
            return new Result(0, null, availableStock > 0 ? StockState.NO_ROTATION : StockState.NO_MOVEMENT);
        }
        double med = availableStock / cpma;
        StockState state;
        if (availableStock == 0) {
            state = StockState.STOCKOUT;
        } else if (med < UNDERSTOCK_BELOW) {
            state = StockState.UNDERSTOCK;
        } else if (med <= OVERSTOCK_ABOVE) {
            state = StockState.NORMAL_STOCK;
        } else {
            state = StockState.OVERSTOCK;
        }
        return new Result(cpma, med, state);
    }
}
