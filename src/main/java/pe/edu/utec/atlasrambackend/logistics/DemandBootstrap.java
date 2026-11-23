package pe.edu.utec.atlasrambackend.logistics;

import pe.edu.utec.atlasrambackend.stats.RandomVariates;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Distribución de la demanda durante el intervalo de protección (R + L) por el bootstrap de
 * Willemain, Smart y Schwarz (2004, Int J Forecast 20:375-387), pensado para demanda intermitente:
 * <ol>
 *   <li>Si habrá demanda en cada mes se simula con una cadena de Markov de dos estados (con / sin
 *       demanda), estimada de la historia: captura que los meses con demanda vienen en rachas.</li>
 *   <li>El tamaño de cada demanda se remuestrea de las demandas no nulas observadas, con "jittering"
 *       1 + INT(x + Z·√x) para generar valores cercanos que no aparecieron en la historia.</li>
 * </ol>
 * No supone normalidad: con demanda intermitente la normal subestima el percentil alto, que es justo
 * el que define el stock de seguridad.
 */
public final class DemandBootstrap {

    public static final int DRAWS = 5_000;

    private DemandBootstrap() {
    }

    /** Demanda media y percentil del nivel de servicio sobre el horizonte, en unidades. */
    public record Result(double mean, double quantile) {
    }

    /**
     * @param series demanda por mes (al menos 2 meses con demanda)
     * @param horizonMonths duración del intervalo de protección en meses (puede ser fraccionaria)
     * @param serviceLevel probabilidad de no quedarse sin stock en el ciclo, entre 0 y 1
     */
    public static Result simulate(int[] series, double horizonMonths, double serviceLevel, int draws, long seed) {
        if (horizonMonths <= 0) {
            throw new IllegalArgumentException("El horizonte debe ser positivo");
        }
        List<Integer> sizes = new ArrayList<>();
        int[][] transitions = new int[2][2];
        for (int t = 0; t < series.length; t++) {
            if (series[t] > 0) {
                sizes.add(series[t]);
            }
            if (t > 0) {
                transitions[series[t - 1] > 0 ? 1 : 0][series[t] > 0 ? 1 : 0]++;
            }
        }
        if (sizes.size() < DemandClassifier.MIN_NONZERO) {
            throw new IllegalArgumentException("Se necesitan al menos 2 meses con demanda");
        }
        double overall = (double) sizes.size() / series.length;
        double fromZero = probability(transitions[0], overall);
        double fromDemand = probability(transitions[1], overall);

        int wholeMonths = (int) Math.ceil(horizonMonths);
        double lastFraction = horizonMonths - (wholeMonths - 1);
        boolean lastObserved = series[series.length - 1] > 0;

        RandomVariates random = new RandomVariates(seed);
        double[] totals = new double[draws];
        double sum = 0;
        for (int draw = 0; draw < draws; draw++) {
            boolean state = lastObserved;
            double total = 0;
            for (int month = 1; month <= wholeMonths; month++) {
                state = random.uniform() < (state ? fromDemand : fromZero);
                if (state) {
                    int size = jitter(sizes.get(random.nextInt(sizes.size())), random);
                    total += month == wholeMonths ? lastFraction * size : size;
                }
            }
            totals[draw] = total;
            sum += total;
        }
        Arrays.sort(totals);
        int index = Math.min(draws - 1, Math.max(0, (int) Math.ceil(serviceLevel * draws) - 1));
        return new Result(sum / draws, totals[index]);
    }

    static int jitter(int size, RandomVariates random) {
        // X* = 1 + INT(X + Z·√X), con INT = parte entera, como en el artículo original.
        long jittered = 1 + (long) Math.floor(size + random.normal() * Math.sqrt(size));
        return jittered <= 0 ? size : (int) jittered;
    }

    private static double probability(int[] fromState, double fallback) {
        int total = fromState[0] + fromState[1];
        return total == 0 ? fallback : (double) fromState[1] / total;
    }
}
