package pe.edu.utec.atlasrambackend.alert;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Detecta un aumento anómalo de resistencia comparando una ventana reciente con la línea base
 * del mismo establecimiento, con una prueba binomial exacta de una cola.
 *
 * Hipótesis nula: la probabilidad de resistencia en la ventana es la misma que en la línea base.
 * Si con esa probabilidad es muy improbable observar tantos resistentes (p menor que alpha),
 * hay señal. Es el mismo principio que usan los sistemas de vigilancia (comparar lo observado
 * con lo esperado), aplicado a proporciones porque el número de cultivos varía de mes a mes.
 *
 * Calibración con el seed (17 hospitales, evaluación semanal durante 12 meses): con alpha = 0.0005,
 * al menos 4 resistentes y 20 resultados de línea base, el brote de K. pneumoniae resistente a
 * carbapenémicos del HNHU se detecta el 27/04/2026 y la red completa genera 5 alertas en el año.
 */
@Component
public class OutbreakDetector {

    private final double alpha;
    private final int minResistant;
    private final int minBaselineTested;

    public OutbreakDetector(@Value("${app.alert.alpha:0.0005}") double alpha,
                            @Value("${app.alert.min-resistant:4}") int minResistant,
                            @Value("${app.alert.min-baseline-tested:20}") int minBaselineTested) {
        this.alpha = alpha;
        this.minResistant = minResistant;
        this.minBaselineTested = minBaselineTested;
    }

    public Optional<OutbreakSignal> evaluate(long tested, long resistant,
                                             long baselineTested, long baselineResistant) {
        if (tested <= 0 || resistant < minResistant || baselineTested < minBaselineTested) {
            return Optional.empty();
        }
        double observed = (double) resistant / tested;
        // Suavizado (r + 0.5) / (n + 1): evita una tasa base de 0 %, con la que cualquier resistente
        // sería "imposible" y dispararía la alerta sin evidencia suficiente.
        double expected = (baselineResistant + 0.5) / (baselineTested + 1.0);
        if (observed <= expected) {
            return Optional.empty();
        }
        double pValue = binomialUpperTail((int) tested, (int) resistant, expected);
        return pValue < alpha ? Optional.of(new OutbreakSignal(observed, expected, pValue)) : Optional.empty();
    }

    /** P(X >= k) con X ~ Binomial(n, p), calculada en escala logarítmica para no desbordar. */
    static double binomialUpperTail(int n, int k, double p) {
        if (k <= 0) {
            return 1.0;
        }
        if (k > n) {
            return 0.0;
        }
        double logP = Math.log(p);
        double logQ = Math.log1p(-p);
        double sum = 0.0;
        for (int x = k; x <= n; x++) {
            sum += Math.exp(logBinomialCoefficient(n, x) + x * logP + (n - x) * logQ);
        }
        return Math.min(1.0, sum);
    }

    private static double logBinomialCoefficient(int n, int k) {
        int m = Math.min(k, n - k);
        double result = 0.0;
        for (int i = 1; i <= m; i++) {
            result += Math.log(n - m + i) - Math.log(i);
        }
        return result;
    }
}
