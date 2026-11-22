package pe.edu.utec.atlasrambackend.wisca;

import pe.edu.utec.atlasrambackend.model.Interpretation;
import pe.edu.utec.atlasrambackend.stats.RandomVariates;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WISCA bayesiano (Weighted-Incidence Syndromic Combination Antibiogram).
 *
 * El antibiograma clásico responde "¿qué % de E. coli es sensible a ceftriaxona?". El médico que recibe
 * a un paciente con bacteriemia no sabe todavía cuál es el germen; su pregunta es "¿qué probabilidad hay
 * de que ESTE tratamiento cubra lo que sea que tenga?". El WISCA pondera la sensibilidad de cada germen
 * por su frecuencia en el síndrome:
 *
 *     cobertura(régimen) = Σ_g  P(germen g | síndrome) · P(g sensible a al menos un fármaco del régimen)
 *
 * Modelo (Hebert et al., 2012; Bielicki et al., 2016):
 *   frecuencias   θ ~ Dirichlet(n_g + 1)        a priori uniforme sobre los gérmenes observados
 *   sensibilidad  φ_g ~ Beta(k_g + 1, m_g − k_g + 1)   a priori uniforme Beta(1, 1)
 * y la cobertura se obtiene por Monte Carlo, con las mismas θ para todos los regímenes, lo que permite
 * calcular la probabilidad de que cada régimen sea el mejor.
 *
 * Combinaciones: un aislamiento está cubierto si es sensible a AL MENOS UNO de los fármacos, medido en
 * el MISMO aislamiento (no se asume independencia).
 *
 * Fármacos que el laboratorio NUNCA prueba frente a un germen (ceftriaxona frente a P. aeruginosa,
 * vancomicina frente a E. coli) se consideran sin actividad contra él: casi siempre es una resistencia
 * intrínseca. Es la suposición conservadora. Así, en MEM + VAN, un E. coli resistente a meropenem cuenta
 * como NO cubierto (y no como "no evaluable", que lo sacaría del cálculo e inflaría la cobertura).
 * Un germen para el que no se prueba ningún fármaco del régimen cuenta como no cubierto y se informa.
 */
public final class WiscaCalculator {

    /** Un aislamiento (primer aislamiento del paciente) con su perfil de sensibilidad. */
    public record IsolateProfile(Long microorganismId, String microorganismCode, String microorganismName,
                                 Map<String, Interpretation> results) {
    }

    public record PathogenCoverage(String microorganismName, int evaluated, int covered, Double percentage) {
    }

    public record RegimenResult(List<String> antibiotics, double mean, double median, double low, double high,
                                double probabilityAboveTarget, double probabilityBest,
                                List<PathogenCoverage> byPathogen, List<String> untestedPathogens,
                                double untestedFrequency) {
    }

    public record PathogenFrequency(String microorganismName, int isolates, double frequency) {
    }

    public record Result(int isolates, List<PathogenFrequency> pathogens, List<RegimenResult> regimens) {
    }

    private WiscaCalculator() {
    }

    /**
     * @param target cobertura objetivo en %, para P(cobertura ≥ objetivo); las guías suelen pedir ≥ 90 %
     */
    public static Result compute(List<IsolateProfile> isolates, List<List<String>> regimens,
                                 double target, int draws, long seed) {
        // Gérmenes en orden de frecuencia.
        Map<Long, List<IsolateProfile>> byPathogen = new LinkedHashMap<>();
        isolates.stream()
                .map(i -> new IsolateProfile(i.microorganismId(), i.microorganismCode(), i.microorganismName(),
                        ExpertRules.apply(i.microorganismCode(), i.results())))
                .forEach(i -> byPathogen.computeIfAbsent(i.microorganismId(), k -> new ArrayList<>()).add(i));
        List<List<IsolateProfile>> groups = new ArrayList<>(byPathogen.values());
        groups.sort((a, b) -> Integer.compare(b.size(), a.size()));

        int k = groups.size();
        int total = isolates.size();
        if (k == 0) {
            return new Result(0, List.of(), List.of());
        }
        double[] alpha = new double[k];
        List<PathogenFrequency> frequencies = new ArrayList<>();
        for (int g = 0; g < k; g++) {
            alpha[g] = groups.get(g).size() + 1;
            frequencies.add(new PathogenFrequency(groups.get(g).get(0).microorganismName(), groups.get(g).size(),
                    100 * alpha[g] / (total + k)));   // media a posteriori de la Dirichlet
        }

        // Fármacos que se probaron al menos una vez frente a cada germen.
        List<java.util.Set<String>> testedDrugs = new ArrayList<>();
        for (List<IsolateProfile> group : groups) {
            java.util.Set<String> tested = new java.util.HashSet<>();
            group.forEach(i -> tested.addAll(i.results().keySet()));
            testedDrugs.add(tested);
        }

        // Cubiertos y evaluables por régimen y germen.
        int r = regimens.size();
        int[][] evaluated = new int[r][k];
        int[][] covered = new int[r][k];
        for (int j = 0; j < r; j++) {
            for (int g = 0; g < k; g++) {
                // Solo cuentan los fármacos que alguna vez se probaron frente a este germen.
                List<String> effective = regimens.get(j).stream().filter(testedDrugs.get(g)::contains).toList();
                if (effective.isEmpty()) {
                    continue;
                }
                for (IsolateProfile isolate : groups.get(g)) {
                    Boolean c = coverage(isolate.results(), effective);
                    if (c != null) {
                        evaluated[j][g]++;
                        if (c) {
                            covered[j][g]++;
                        }
                    }
                }
            }
        }

        // Monte Carlo con sorteos pareados entre regímenes.
        RandomVariates rv = new RandomVariates(seed);
        double[][] samples = new double[r][draws];
        int[] best = new int[r];
        for (int d = 0; d < draws; d++) {
            double[] theta = rv.dirichlet(alpha);
            int bestIndex = -1;
            double bestValue = -1;
            for (int j = 0; j < r; j++) {
                double value = 0;
                for (int g = 0; g < k; g++) {
                    if (evaluated[j][g] > 0) {
                        value += theta[g] * rv.beta(covered[j][g] + 1, evaluated[j][g] - covered[j][g] + 1);
                    }
                }
                samples[j][d] = value * 100;
                if (value > bestValue) {
                    bestValue = value;
                    bestIndex = j;
                }
            }
            best[bestIndex]++;
        }

        List<RegimenResult> results = new ArrayList<>();
        for (int j = 0; j < r; j++) {
            double[] s = samples[j];
            double mean = Arrays.stream(s).average().orElse(0);
            long above = Arrays.stream(s).filter(v -> v >= target).count();
            Arrays.sort(s);
            List<PathogenCoverage> perPathogen = new ArrayList<>();
            List<String> untested = new ArrayList<>();
            double untestedFrequency = 0;
            for (int g = 0; g < k; g++) {
                String name = groups.get(g).get(0).microorganismName();
                int e = evaluated[j][g];
                perPathogen.add(new PathogenCoverage(name, e, covered[j][g], e == 0 ? null : 100.0 * covered[j][g] / e));
                if (e == 0) {
                    untested.add(name);
                    untestedFrequency += frequencies.get(g).frequency();
                }
            }
            // La cobertura real está entre la conservadora (mediana) y esa más untestedFrequency:
            // es cuánto podría cambiar el resultado si los gérmenes sin pruebas sí estuvieran cubiertos.
            results.add(new RegimenResult(regimens.get(j), mean, quantile(s, 0.5), quantile(s, 0.025),
                    quantile(s, 0.975), (double) above / draws, (double) best[j] / draws, perPathogen, untested,
                    untestedFrequency));
        }
        results.sort((a, b) -> Double.compare(b.median(), a.median()));
        return new Result(total, frequencies, results);
    }

    /** true = cubierto, false = no cubierto, null = no evaluable (faltan pruebas y ninguna es S). */
    static Boolean coverage(Map<String, Interpretation> results, List<String> regimen) {
        boolean allTested = true;
        for (String code : regimen) {
            Interpretation i = results.get(code);
            if (i == Interpretation.SUSCEPTIBLE) {
                return true;
            }
            if (i == null) {
                allTested = false;
            }
        }
        return allTested ? Boolean.FALSE : null;
    }

    /** Cuantil por interpolación lineal sobre un arreglo ordenado. */
    static double quantile(double[] sorted, double q) {
        double position = q * (sorted.length - 1);
        int lower = (int) Math.floor(position);
        int upper = Math.min(lower + 1, sorted.length - 1);
        return sorted[lower] + (position - lower) * (sorted[upper] - sorted[lower]);
    }
}
