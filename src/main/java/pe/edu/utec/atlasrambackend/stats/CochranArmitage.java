package pe.edu.utec.atlasrambackend.stats;

import java.util.List;

/**
 * Prueba de tendencia de Cochran-Armitage para proporciones ordenadas en el tiempo.
 *
 * Pregunta: ¿la proporción de resistentes cambia de forma sostenida a lo largo de los periodos, o las
 * subidas y bajadas son ruido? Compara cada periodo con la proporción global, ponderado por su número
 * de aislamientos y por su posición en el tiempo (puntajes 0, 1, 2…):
 *
 *   T  = Σ tᵢ (xᵢ − nᵢ p̄)
 *   Var(T) = p̄ (1 − p̄) [ Σ nᵢ tᵢ² − (Σ nᵢ tᵢ)² / N ]
 *   z  = T / √Var(T)            p = 2 · (1 − Φ(|z|))
 *
 * La pendiente que se informa es la de mínimos cuadrados ponderados de pᵢ sobre tᵢ (pesos nᵢ), que es la
 * recta cuya pendiente contrasta la prueba (Armitage, 1955; Agresti, Categorical Data Analysis, §5.3.5).
 */
public final class CochranArmitage {

    private CochranArmitage() {
    }

    /** Un periodo: posición en el tiempo, resistentes y probados. */
    public record Point(double score, long events, long trials) {
    }

    /**
     * z, valor p bilateral y pendiente (proporción por unidad de puntaje). Null si no hay al menos
     * dos periodos con datos o si la proporción global es 0 o 1 (sin variación no hay nada que probar).
     */
    public record Result(double z, double pValue, double slope, int periods, long totalTrials) {
    }

    public static Result test(List<Point> points) {
        List<Point> used = points.stream().filter(p -> p.trials() > 0).toList();
        if (used.size() < 2) {
            return null;
        }
        long n = 0;
        long x = 0;
        double sumNT = 0;
        double sumNT2 = 0;
        for (Point p : used) {
            n += p.trials();
            x += p.events();
            sumNT += p.trials() * p.score();
            sumNT2 += p.trials() * p.score() * p.score();
        }
        double pBar = (double) x / n;
        double scoreVariance = sumNT2 - sumNT * sumNT / n;
        if (pBar == 0 || pBar == 1 || scoreVariance <= 0) {
            return null;
        }
        double t = 0;
        double sxy = 0;
        double meanScore = sumNT / n;
        for (Point p : used) {
            t += p.score() * (p.events() - p.trials() * pBar);
            double pi = (double) p.events() / p.trials();
            sxy += p.trials() * (p.score() - meanScore) * (pi - pBar);
        }
        double z = t / Math.sqrt(pBar * (1 - pBar) * scoreVariance);
        double pValue = 2 * (1 - Normal.cdf(Math.abs(z)));
        return new Result(z, Math.min(1, pValue), sxy / scoreVariance, used.size(), n);
    }
}
