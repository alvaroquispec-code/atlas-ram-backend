package pe.edu.utec.atlasrambackend.logistics;

import pe.edu.utec.atlasrambackend.model.AwareCategory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Consumo hospitalario de antibióticos en DDD por 100 camas-día (metodología ATC/DDD de la OMS):
 * DDD = gramos consumidos ÷ DDD de la vía; indicador = DDD × 100 ÷ días-cama ocupados.
 * Se desagrega por grupo AWaRe. La meta de la OMS (al menos 60 % del consumo en Access) es una meta
 * NACIONAL; en un hospital, que atiende casos más graves, se usa como referencia y no como umbral.
 */
public final class DddIndicator {

    public static final double WHO_ACCESS_TARGET = 0.60;

    private DddIndicator() {
    }

    public record Consumption(String label, AwareCategory aware, double grams, Double dddGrams) {
    }

    public record GroupTotal(AwareCategory aware, double ddd, Double dddPer100BedDays, double share) {
    }

    public record Result(double totalDdd, Double dddPer100BedDays, List<GroupTotal> byAware,
                         Double accessShare, List<String> withoutDdd, List<String> withoutAware) {
    }

    public static Result compute(List<Consumption> rows, Double occupiedBedDays) {
        Map<AwareCategory, Double> byGroup = new EnumMap<>(AwareCategory.class);
        double total = 0;
        double unclassified = 0;
        List<String> withoutDdd = new ArrayList<>();
        List<String> withoutAware = new ArrayList<>();
        for (Consumption row : rows) {
            if (row.dddGrams() == null || row.dddGrams() <= 0) {
                withoutDdd.add(row.label());
                continue;
            }
            double ddd = row.grams() / row.dddGrams();
            total += ddd;
            if (row.aware() == null) {
                withoutAware.add(row.label());
                unclassified += ddd;
            } else {
                byGroup.merge(row.aware(), ddd, Double::sum);
            }
        }
        List<GroupTotal> groups = new ArrayList<>();
        for (Map.Entry<AwareCategory, Double> entry : byGroup.entrySet()) {
            groups.add(new GroupTotal(entry.getKey(), entry.getValue(), per100(entry.getValue(), occupiedBedDays),
                    total == 0 ? 0 : entry.getValue() / total));
        }
        double classified = total - unclassified;
        Double accessShare = classified == 0 ? null : byGroup.getOrDefault(AwareCategory.ACCESS, 0.0) / classified;
        return new Result(total, per100(total, occupiedBedDays), groups, accessShare, withoutDdd, withoutAware);
    }

    private static Double per100(double ddd, Double bedDays) {
        return bedDays == null || bedDays <= 0 ? null : ddd * 100 / bedDays;
    }
}
