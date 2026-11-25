package pe.edu.utec.atlasrambackend.logistics;

/**
 * Clasificación SBC (Syntetos, Boylan y Croston, 2005, J Oper Res Soc 56:495-503).
 * ADI = periodos observados ÷ periodos con demanda; CV² = (desviación ÷ media)² de las demandas no nulas.
 */
public final class DemandClassifier {

    public static final double ADI_CUTOFF = 1.32;
    public static final double CV2_CUTOFF = 0.49;
    public static final int MIN_PERIODS = 6;
    public static final int MIN_NONZERO = 2;

    private DemandClassifier() {
    }

    public record Result(DemandPattern pattern, Double adi, Double cv2, int nonZeroPeriods) {
    }

    public static Result classify(int[] series) {
        int nonZero = 0;
        double sum = 0;
        for (int d : series) {
            if (d > 0) {
                nonZero++;
                sum += d;
            }
        }
        if (series.length < MIN_PERIODS || nonZero < MIN_NONZERO) {
            return new Result(DemandPattern.INSUFFICIENT_HISTORY, null, null, nonZero);
        }
        double mean = sum / nonZero;
        double squares = 0;
        for (int d : series) {
            if (d > 0) {
                squares += (d - mean) * (d - mean);
            }
        }
        double cv2 = (squares / nonZero) / (mean * mean);
        double adi = (double) series.length / nonZero;

        boolean frequent = adi < ADI_CUTOFF;
        boolean stableSize = cv2 < CV2_CUTOFF;
        DemandPattern pattern = frequent
                ? (stableSize ? DemandPattern.SMOOTH : DemandPattern.ERRATIC)
                : (stableSize ? DemandPattern.INTERMITTENT : DemandPattern.LUMPY);
        return new Result(pattern, adi, cv2, nonZero);
    }
}
