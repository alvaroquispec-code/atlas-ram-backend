package pe.edu.utec.atlasrambackend.stats;

/** Distribución normal estándar. */
public final class Normal {

    private Normal() {
    }

    /**
     * Φ(z) = 1 − ½·erfc(z/√2). erfc por la aproximación de Chebyshev de Numerical Recipes (erfcc),
     * con error relativo menor que 1,2·10⁻⁷ en todo el rango: sobra para valores p.
     */
    public static double cdf(double z) {
        return 1 - 0.5 * erfc(z / Math.sqrt(2));
    }

    static double erfc(double x) {
        double t = 1 / (1 + 0.5 * Math.abs(x));
        double y = t * Math.exp(-x * x - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418
                + t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587
                + t * (-0.82215223 + t * 0.17087277)))))))));
        return x >= 0 ? y : 2 - y;
    }
}
