package pe.edu.utec.atlasrambackend.logistics;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Conecta la vigilancia con el inventario. Cuando un establecimiento tiene una alerta de brote activa
 * (resistencia de un microorganismo a una clase de antibiótico por encima de su línea base), los médicos
 * pasan a los fármacos que TODAVÍA funcionan contra ese germen. Si uno de ellos tiene poca cobertura, el
 * riesgo de quiebre es mayor que lo que dice su historia de consumo, que aún no refleja el brote.
 *
 * Fármaco de escalamiento = de otra clase que la de la alerta y con al menos 80 % de sensibles frente al
 * germen en alerta, en el mismo establecimiento y los últimos 12 meses, con al menos 30 resultados (el mínimo
 * de CLSI M39). Así lo decide el antibiograma del propio laboratorio, no una lista fija: para una Klebsiella
 * resistente a carbapenémicos sale amikacina, y nunca vancomicina, que no se prueba contra ese germen.
 *
 * Regla (señal para revisar, no pronóstico): fármaco de escalamiento ∧ cobertura &lt; 1.5 × (R + L).
 * El 80 % y el factor 1.5 son supuestos de diseño configurables, no valores de la literatura.
 */
public final class OutbreakPressure {

    public static final double STRESS_FACTOR = 1.5;
    public static final double MIN_SUSCEPTIBLE_PERCENT = 80;
    public static final int MIN_TESTED = 30;

    private OutbreakPressure() {
    }

    /** activeDrugs: códigos con ≥ 80 % de sensibles frente al germen de la alerta (ver activeDrug). */
    public record ActiveAlert(String microorganism, String antibioticClass, Set<String> activeDrugs) {
    }

    /** ¿El antibiótico sigue siendo activo frente al germen según el antibiograma local? */
    public static boolean activeDrug(long tested, long susceptible) {
        return tested >= MIN_TESTED && susceptible * 100.0 / tested >= MIN_SUSCEPTIBLE_PERCENT;
    }

    public static Optional<String> evaluate(List<ActiveAlert> alerts, String antibioticCode, String antibioticClass,
                                            int available, Double coverageDays, int protectionDays) {
        List<ActiveAlert> driving = alerts.stream()
                .filter(a -> antibioticClass == null || !antibioticClass.equalsIgnoreCase(a.antibioticClass()))
                .filter(a -> a.activeDrugs().contains(antibioticCode))
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
                "Alerta activa: %s resistente a %s%s. Este fármaco sigue siendo activo contra ese germen y tiene %s;"
                        + " se recomienda cubrir al menos %.0f días.",
                first.microorganism(), first.antibioticClass(),
                driving.size() > 1 ? " (y " + (driving.size() - 1) + " alerta(s) más)" : "",
                coverage, required));
    }
}
