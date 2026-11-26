package pe.edu.utec.atlasrambackend.stats;

import java.util.List;

/**
 * Estimación con muestras pequeñas por "pooling parcial" (Bayes empírico beta-binomial).
 *
 * Problema: un distrito con 8 aislamientos y 4 resistentes muestra 50 %, pero con tan pocos datos ese
 * número es casi ruido. CLSI M39 pide al menos 30 aislamientos y, por debajo, no publicar el porcentaje.
 * Este estimador no reemplaza esa regla: da una segunda lectura, explícita y cuantificada.
 *
 * Modelo: la resistencia de cada grupo (distrito o establecimiento) p_i ~ Beta(μ·κ, (1−μ)·κ), y los
 * resistentes r_i ~ Binomial(n_i, p_i). μ es la resistencia media de la red y κ mide cuánto se parecen
 * los grupos entre sí (κ grande: casi iguales; κ chico: muy distintos). μ y κ se estiman con los datos de
 * todos los grupos (método de momentos de Kleinman, 1973) y la estimación de cada grupo es la media
 * posterior (r + μκ) / (n + κ): con n chico se acerca a la media de la red; con n grande respeta el dato
 * local. Es el mismo principio de la "small area estimation" (Efron y Morris, 1975).
 *
 * El intervalo es el de credibilidad al 95 % de la posterior Beta(r + μκ, n − r + (1 − μ)κ).
 */
public final class BetaBinomialPooling {

    /** Mínimo de grupos con datos para estimar cuánto varían entre sí. */
    public static final int MIN_GROUPS = 3;
    /**
     * Correlación intraclase mínima. Si los grupos no varían más de lo que explica el azar, el estimador
     * de momentos da ρ ≤ 0 (κ infinito: todos iguales a la media). Se acota para que el dato local siga
     * contando algo.
     */
    static final double MIN_RHO = 0.001;
    static final double MAX_RHO = 0.999;

    private BetaBinomialPooling() {
    }

    /** Resistentes y probados de un grupo. */
    public record Group(long resistant, long tested) {
        public Group {
            if (tested < 0 || resistant < 0 || resistant > tested) {
                throw new IllegalArgumentException("Se requiere 0 ≤ resistentes ≤ probados");
            }
        }
    }

    /** Distribución a priori estimada de la red: media μ (0–1) y concentración κ (> 0). */
    public record Prior(double mean, double concentration) {
    }

    /** Estimación de un grupo, en porcentaje (0–100). */
    public record Estimate(double percentage, double low, double high) {
    }

    /**
     * Estima μ y κ con los grupos que tienen datos. Null si hay menos de MIN_GROUPS grupos con datos o
     * si la resistencia de la red es 0 % o 100 % (no hay variación que estimar).
     */
    public static Prior fit(List<Group> groups) {
        List<Group> withData = groups.stream().filter(g -> g.tested() > 0).toList();
        int k = withData.size();
        if (k < MIN_GROUPS) {
            return null;
        }
        double total = 0;
        double resistant = 0;
        double sumSquares = 0;
        for (Group g : withData) {
            total += g.tested();
            resistant += g.resistant();
            sumSquares += (double) g.tested() * g.tested();
        }
        double mu = resistant / total;
        if (mu <= 0 || mu >= 1) {
            return null;
        }
        // S = Σ n_i (p_i − μ)²; E[S] = μ(1−μ)[(k−1) + ρ(N − Σn_i²/N − (k−1))]
        double s = 0;
        for (Group g : withData) {
            double p = (double) g.resistant() / g.tested();
            s += g.tested() * (p - mu) * (p - mu);
        }
        double binomial = mu * (1 - mu);
        double denominator = binomial * (total - sumSquares / total - (k - 1));
        double rho = denominator > 0 ? (s - binomial * (k - 1)) / denominator : MIN_RHO;
        rho = Math.min(MAX_RHO, Math.max(MIN_RHO, rho));
        return new Prior(mu, (1 - rho) / rho);
    }

    /** Estimación de un grupo con la a priori de la red. Null si el grupo no tiene datos. */
    public static Estimate estimate(Prior prior, long resistant, long tested) {
        if (prior == null || tested <= 0) {
            return null;
        }
        double a = resistant + prior.mean() * prior.concentration();
        double b = tested - resistant + (1 - prior.mean()) * prior.concentration();
        return new Estimate(100 * a / (a + b), 100 * betaQuantile(0.025, a, b), 100 * betaQuantile(0.975, a, b));
    }

    /** Cuantil de la distribución Beta(a, b), por bisección sobre la función de distribución. */
    static double betaQuantile(double probability, double a, double b) {
        double low = 0;
        double high = 1;
        for (int i = 0; i < 100; i++) {
            double mid = (low + high) / 2;
            if (regularizedBeta(mid, a, b) < probability) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) / 2;
    }

    /** Función beta incompleta regularizada I_x(a, b) (fracción continua de Lentz, Numerical Recipes 6.4). */
    static double regularizedBeta(double x, double a, double b) {
        if (x <= 0) {
            return 0;
        }
        if (x >= 1) {
            return 1;
        }
        double logFront = logGamma(a + b) - logGamma(a) - logGamma(b) + a * Math.log(x) + b * Math.log(1 - x);
        if (x < (a + 1) / (a + b + 2)) {
            return Math.exp(logFront) * continuedFraction(x, a, b) / a;
        }
        return 1 - Math.exp(logFront) * continuedFraction(1 - x, b, a) / b;
    }

    private static double continuedFraction(double x, double a, double b) {
        final double tiny = 1e-300;
        double c = 1;
        double d = 1 - (a + b) * x / (a + 1);
        d = 1 / (Math.abs(d) < tiny ? tiny : d);
        double h = d;
        for (int m = 1; m <= 300; m++) {
            int m2 = 2 * m;
            double aa = m * (b - m) * x / ((a + m2 - 1) * (a + m2));
            d = 1 + aa * d;
            d = 1 / (Math.abs(d) < tiny ? tiny : d);
            c = 1 + aa / c;
            c = Math.abs(c) < tiny ? tiny : c;
            h *= d * c;
            aa = -(a + m) * (a + b + m) * x / ((a + m2) * (a + m2 + 1));
            d = 1 + aa * d;
            d = 1 / (Math.abs(d) < tiny ? tiny : d);
            c = 1 + aa / c;
            c = Math.abs(c) < tiny ? tiny : c;
            double delta = d * c;
            h *= delta;
            if (Math.abs(delta - 1) < 1e-14) {
                break;
            }
        }
        return h;
    }

    /** Logaritmo de la función gamma (aproximación de Lanczos, g = 7, error relativo < 1e-13). */
    static double logGamma(double x) {
        final double[] coefficients = {0.99999999999980993, 676.5203681218851, -1259.1392167224028,
                771.32342877765313, -176.61502916214059, 12.507343278686905, -0.13857109526572012,
                9.9843695780195716e-6, 1.5056327351493116e-7};
        if (x < 0.5) {
            return Math.log(Math.PI / Math.abs(Math.sin(Math.PI * x))) - logGamma(1 - x);
        }
        double z = x - 1;
        double sum = coefficients[0];
        for (int i = 1; i < coefficients.length; i++) {
            sum += coefficients[i] / (z + i);
        }
        double t = z + 7.5;
        return 0.5 * Math.log(2 * Math.PI) + (z + 0.5) * Math.log(t) - t + Math.log(sum);
    }
}
