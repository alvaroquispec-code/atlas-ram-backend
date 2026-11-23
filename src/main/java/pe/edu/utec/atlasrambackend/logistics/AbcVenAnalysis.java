package pe.edu.utec.atlasrambackend.logistics;

import pe.edu.utec.atlasrambackend.model.VenClass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Análisis ABC (valor) cruzado con VEN (criticidad clínica), según la OMS y MSH (MDS-3, cap. 40).
 * <ul>
 *   <li>Categoría I (máxima atención): AV, AE, AN, BV, CV. Todo lo caro y todo lo vital.</li>
 *   <li>Categoría II: BE, BN, CE.</li>
 *   <li>Categoría III: CN. Candidatos a racionalizar.</li>
 * </ul>
 */
public final class AbcVenAnalysis {

    static final double A_SHARE = 0.80;
    static final double B_SHARE = 0.95;

    private AbcVenAnalysis() {
    }

    /**
     * Clasifica por valor anual. Un ítem es A mientras el acumulado ANTES de sumarlo sea menor al 80 %,
     * así el ítem que cruza el umbral también es A.
     */
    public static Map<Long, AbcClass> classify(Map<Long, Double> annualValueByItem) {
        Map<Long, AbcClass> result = new HashMap<>();
        double total = annualValueByItem.values().stream().mapToDouble(Double::doubleValue).sum();
        List<Map.Entry<Long, Double>> ordered = new ArrayList<>(annualValueByItem.entrySet());
        ordered.sort(Map.Entry.<Long, Double>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()));
        double cumulative = 0;
        for (Map.Entry<Long, Double> entry : ordered) {
            double before = total == 0 ? 1 : cumulative / total;
            AbcClass abc;
            if (entry.getValue() <= 0) {
                abc = AbcClass.C;
            } else if (before < A_SHARE) {
                abc = AbcClass.A;
            } else if (before < B_SHARE) {
                abc = AbcClass.B;
            } else {
                abc = AbcClass.C;
            }
            result.put(entry.getKey(), abc);
            cumulative += entry.getValue();
        }
        return result;
    }

    /** Categoría de la matriz ABC-VEN (1, 2 o 3); nulo si falta la clase ABC (ítem sin costo). */
    public static Integer category(AbcClass abc, VenClass ven) {
        if (abc == null) {
            return null;
        }
        if (abc == AbcClass.A || ven == VenClass.VITAL) {
            return 1;
        }
        if (abc == AbcClass.C && ven == VenClass.NON_ESSENTIAL) {
            return 3;
        }
        return 2;
    }
}
