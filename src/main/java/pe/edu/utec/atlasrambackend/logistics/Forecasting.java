package pe.edu.utec.atlasrambackend.logistics;

/**
 * Pronósticos de demanda por periodo (mes). Devuelven la demanda media esperada del próximo periodo.
 * <ul>
 *   <li>SES: Brown (1959).</li>
 *   <li>SBA: Syntetos y Boylan (2005, Int J Forecast 21:303-314). Croston (1972) estima tamaño z e
 *       intervalo p por separado; z/p está sesgado hacia arriba y SBA lo corrige con (1 − α/2).</li>
 *   <li>TSB: Teunter, Syntetos y Babai (2011, Eur J Oper Res 214:606-615). Actualiza la PROBABILIDAD de
 *       demanda en cada periodo, también en los periodos en cero, así que el pronóstico decae cuando la
 *       demanda se apaga (Croston y SBA no lo hacen y siguen pidiendo un ítem obsoleto).</li>
 * </ul>
 */
public final class Forecasting {

    public static final double ALPHA = 0.1;
    public static final double BETA = 0.1;
    public static final int TRAILING_ZEROS_FOR_TSB = 3;

    private Forecasting() {
    }

    public static ForecastMethod choose(DemandPattern pattern, int[] series) {
        return switch (pattern) {
            case INSUFFICIENT_HISTORY -> ForecastMethod.NONE;
            case SMOOTH, ERRATIC -> ForecastMethod.SES;
            case INTERMITTENT, LUMPY -> trailingZeros(series) >= TRAILING_ZEROS_FOR_TSB
                    ? ForecastMethod.TSB : ForecastMethod.SBA;
        };
    }

    public static double forecast(ForecastMethod method, int[] series) {
        return switch (method) {
            case SES -> ses(series, ALPHA);
            case SBA -> sba(series, ALPHA);
            case TSB -> tsb(series, ALPHA, BETA);
            case NONE -> throw new IllegalArgumentException("Sin método de pronóstico");
        };
    }

    public static double ses(int[] series, double alpha) {
        double level = series[0];
        for (int t = 1; t < series.length; t++) {
            level += alpha * (series[t] - level);
        }
        return level;
    }

    public static double sba(int[] series, double alpha) {
        double size = Double.NaN;
        double interval = Double.NaN;
        int sinceLast = 0;
        for (int d : series) {
            sinceLast++;
            if (d > 0) {
                if (Double.isNaN(size)) {
                    size = d;
                    interval = sinceLast;
                } else {
                    size += alpha * (d - size);
                    interval += alpha * (sinceLast - interval);
                }
                sinceLast = 0;
            }
        }
        if (Double.isNaN(size)) {
            return 0;
        }
        return (1 - alpha / 2) * size / interval;
    }

    public static double tsb(int[] series, double alpha, double beta) {
        int nonZero = 0;
        double sum = 0;
        for (int d : series) {
            if (d > 0) {
                nonZero++;
                sum += d;
            }
        }
        if (nonZero == 0) {
            return 0;
        }
        double probability = (double) nonZero / series.length;
        double size = sum / nonZero;
        for (int d : series) {
            if (d > 0) {
                probability += beta * (1 - probability);
                size += alpha * (d - size);
            } else {
                probability += beta * (0 - probability);
            }
        }
        return probability * size;
    }

    static int trailingZeros(int[] series) {
        int count = 0;
        for (int t = series.length - 1; t >= 0 && series[t] == 0; t--) {
            count++;
        }
        return count;
    }
}
