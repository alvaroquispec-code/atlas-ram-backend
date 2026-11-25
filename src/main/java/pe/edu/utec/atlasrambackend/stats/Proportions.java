package pe.edu.utec.atlasrambackend.stats;

/**
 * Intervalos de confianza para una proporción (x éxitos en n pruebas).
 *
 * Se usa el intervalo de Wilson (1927) y no el de Wald (p ± 1,96·√(p(1−p)/n)), que es el de los libros
 * introductorios: con n pequeño o p cerca de 0 % o 100 % el de Wald da límites imposibles (negativos o
 * mayores que 100 %) y una cobertura real muy por debajo del 95 % (Brown, Cai y DasGupta, 2001).
 * En resistencia antimicrobiana son justo los casos frecuentes: 0 de 12 resistentes, 29 de 30 sensibles.
 */
public final class Proportions {

    /** Cuantil 0,975 de la normal estándar: intervalo al 95 %. */
    public static final double Z_95 = 1.959963984540054;

    private Proportions() {
    }

    /** Intervalo en porcentaje (0–100). */
    public record Interval(double low, double high) {
    }

    /** Intervalo de Wilson al 95 %, en porcentaje. Con n = 0 no hay información: [0, 100]. */
    public static Interval wilson(long successes, long n) {
        return wilson(successes, n, Z_95);
    }

    public static Interval wilson(long successes, long n, double z) {
        if (n < 0 || successes < 0 || successes > n) {
            throw new IllegalArgumentException("Se requiere 0 ≤ éxitos ≤ n (" + successes + " de " + n + ")");
        }
        if (n == 0) {
            return new Interval(0, 100);
        }
        double p = (double) successes / n;
        double z2 = z * z;
        double denominator = 1 + z2 / n;
        double center = (p + z2 / (2.0 * n)) / denominator;
        double half = z * Math.sqrt(p * (1 - p) / n + z2 / (4.0 * n * n)) / denominator;
        // Con x = 0 o x = n el límite exacto es 0 % o 100 %; se fija para evitar residuos de redondeo.
        double low = successes == 0 ? 0 : Math.max(0, center - half);
        double high = successes == n ? 1 : Math.min(1, center + half);
        return new Interval(low * 100, high * 100);
    }

    public static double percentage(long part, long total) {
        return total == 0 ? 0 : part * 100.0 / total;
    }
}
