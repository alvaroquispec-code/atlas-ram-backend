package pe.edu.utec.atlasrambackend.logistics;

import pe.edu.utec.atlasrambackend.wisca.Syndrome;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Conecta la vigilancia con el inventario. Cuando un establecimiento tiene una alerta de brote activa
 * (resistencia de un microorganismo a una clase de antibiótico por encima de su línea base), los médicos
 * escalan a fármacos de OTRA clase. Si uno de esos fármacos de escalamiento tiene poca cobertura, el
 * riesgo de quiebre es mayor que lo que dice su historia de consumo, que todavía no refleja el brote.
 *
 * Regla (señal para revisar, no pronóstico):
 *   alerta activa  ∧  el antibiótico está en algún régimen candidato del WISCA  ∧  su clase difiere de la
 *   clase en alerta  ∧  cobertura &lt; FACTOR_DE_ESTRÉS × (R + L).
 * El factor 1.5 es un supuesto de diseño configurable, no un valor de la literatura: pide cubrir con
 * holgura el intervalo de protección mientras dura el brote.
 */
public final class OutbreakPressure {

    public static final double STRESS_FACTOR = 1.5;
    static final Set<String> ESCALATION_DRUGS = escalationDrugs();

    private OutbreakPressure() {
    }

    public record ActiveAlert(String microorganism, String antibioticClass) {
    }

    public static Optional<String> evaluate(List<ActiveAlert> alerts, String antibioticCode, String antibioticClass,
                                            int available, Double coverageDays, int protectionDays) {
        if (alerts.isEmpty() || !ESCALATION_DRUGS.contains(antibioticCode)) {
            return Optional.empty();
        }
        List<ActiveAlert> driving = alerts.stream()
                .filter(a -> antibioticClass == null || !antibioticClass.equalsIgnoreCase(a.antibioticClass()))
                .toList();
        if (driving.isEmpty()) {
            return Optional.empty();
        }
        double required = STRESS_FACTOR * protectionDays;
        boolean shortCoverage = coverageDays == null ? available == 0 : coverageDays < required;
        if (!shortCoverage) {
            return Optional.empty();
        }
        ActiveAlert first = driving.get(0);
        String coverage = coverageDays == null ? "sin stock disponible"
                : String.format(Locale.ROOT, "solo %.0f días de cobertura", coverageDays);
        return Optional.of(String.format(Locale.ROOT,
                "Alerta activa: %s resistente a %s%s. Este fármaco de escalamiento tiene %s; se recomienda cubrir al menos %.0f días.",
                first.microorganism(), first.antibioticClass(),
                driving.size() > 1 ? " (y " + (driving.size() - 1) + " alerta(s) más)" : "",
                coverage, required));
    }

    private static Set<String> escalationDrugs() {
        Set<String> drugs = new LinkedHashSet<>();
        for (Syndrome syndrome : Syndrome.values()) {
            for (String regimen : syndrome.defaultRegimens()) {
                for (String code : regimen.split("\\+")) {
                    drugs.add(code.trim());
                }
            }
        }
        return Set.copyOf(drugs);
    }
}
