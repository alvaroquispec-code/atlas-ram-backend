package pe.edu.utec.atlasrambackend.stats;

import java.util.SplittableRandom;

/**
 * Generadores de variables aleatorias para la simulación de Monte Carlo del WISCA.
 * Gamma por el método de Marsaglia y Tsang (2000); Beta y Dirichlet a partir de Gammas.
 */
public final class RandomVariates {

    private final SplittableRandom random;

    public RandomVariates(long seed) {
        this.random = new SplittableRandom(seed);
    }

    /** Normal estándar por Box-Muller. */
    public double normal() {
        double u1 = 1 - random.nextDouble();   // (0, 1]: evita log(0)
        double u2 = random.nextDouble();
        return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
    }

    /** Gamma(forma, escala 1). */
    public double gamma(double shape) {
        if (shape <= 0) {
            throw new IllegalArgumentException("La forma de la Gamma debe ser positiva");
        }
        if (shape < 1) {
            // Gamma(a) = Gamma(a + 1) · U^(1/a)
            return gamma(shape + 1) * Math.pow(1 - random.nextDouble(), 1 / shape);
        }
        double d = shape - 1.0 / 3;
        double c = 1 / Math.sqrt(9 * d);
        while (true) {
            double x;
            double v;
            do {
                x = normal();
                v = 1 + c * x;
            } while (v <= 0);
            v = v * v * v;
            double u = 1 - random.nextDouble();
            if (u < 1 - 0.0331 * x * x * x * x || Math.log(u) < 0.5 * x * x + d * (1 - v + Math.log(v))) {
                return d * v;
            }
        }
    }

    /** Beta(a, b) = X / (X + Y), con X ~ Gamma(a), Y ~ Gamma(b). */
    public double beta(double a, double b) {
        double x = gamma(a);
        double y = gamma(b);
        return x / (x + y);
    }

    /** Dirichlet(α₁…αₖ): Gammas normalizadas. */
    public double[] dirichlet(double[] alpha) {
        double[] draw = new double[alpha.length];
        double sum = 0;
        for (int i = 0; i < alpha.length; i++) {
            draw[i] = gamma(alpha[i]);
            sum += draw[i];
        }
        for (int i = 0; i < alpha.length; i++) {
            draw[i] /= sum;
        }
        return draw;
    }
}
