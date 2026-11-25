package pe.edu.utec.atlasrambackend.wisca;

import pe.edu.utec.atlasrambackend.model.Interpretation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Reglas de inferencia de CLSI M100 que los laboratorios aplican al informar, para completar el perfil
 * de un aislamiento con antibióticos que no se prueban directamente. Solo se completa lo que falta:
 * un resultado medido nunca se sobrescribe.
 *
 * S. aureus y la oxacilina (M100, notas de Staphylococcus):
 *   - oxacilina R (SARM) → resistente a todos los betalactámicos (salvo ceftarolina, que aquí no está);
 *   - oxacilina S (SASM) → sensible a los betalactámicos con actividad antiestafilocócica.
 */
public final class ExpertRules {

    static final String STAPHYLOCOCCUS_AUREUS = "sau";
    static final String OXACILLIN = "OXA";

    static final Set<String> BETA_LACTAMS =
            Set.of("AMP", "SAM", "TZP", "OXA", "CZO", "CRO", "CAZ", "FEP", "ETP", "IPM", "MEM");

    /** Ampicilina y ceftazidima no tienen actividad antiestafilocócica fiable: se dejan fuera. */
    static final Set<String> ANTISTAPHYLOCOCCAL_BETA_LACTAMS =
            Set.of("SAM", "TZP", "CZO", "CRO", "FEP", "ETP", "IPM", "MEM");

    private ExpertRules() {
    }

    public static Map<String, Interpretation> apply(String microorganismCode, Map<String, Interpretation> measured) {
        if (!STAPHYLOCOCCUS_AUREUS.equals(microorganismCode) || !measured.containsKey(OXACILLIN)) {
            return measured;
        }
        Map<String, Interpretation> completed = new HashMap<>(measured);
        Interpretation oxacillin = measured.get(OXACILLIN);
        if (oxacillin == Interpretation.RESISTANT) {
            BETA_LACTAMS.forEach(code -> completed.putIfAbsent(code, Interpretation.RESISTANT));
        } else if (oxacillin == Interpretation.SUSCEPTIBLE) {
            ANTISTAPHYLOCOCCAL_BETA_LACTAMS.forEach(code -> completed.putIfAbsent(code, Interpretation.SUSCEPTIBLE));
        }
        return completed;
    }
}
