package pe.edu.utec.atlasrambackend.logistics;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Unidades de cada lote que, al ritmo de consumo actual y despachando primero lo que vence antes (FEFO),
 * llegarán a su fecha de vencimiento sin usarse.
 *
 * Se recorren los lotes en orden de vencimiento. La demanda acumulada hasta el vencimiento del lote i es
 * tasa × días; de ella se descuenta lo que ya absorbieron los lotes anteriores. Lo que el lote i no
 * alcanza a entregar antes de vencer queda en riesgo. Un lote anterior que vence con saldo no consume
 * demanda futura: su saldo simplemente se pierde.
 */
public final class ExpiryRisk {

    private ExpiryRisk() {
    }

    public record Lot(Long id, LocalDate expiryDate, int quantity) {
    }

    public record LotRisk(Long lotId, LocalDate expiryDate, int quantity, int unitsAtRisk) {
    }

    public static List<LotRisk> evaluate(List<Lot> lots, double dailyRate, LocalDate asOf) {
        List<Lot> ordered = new ArrayList<>(lots);
        ordered.sort(Comparator.comparing(Lot::expiryDate).thenComparing(Lot::id,
                Comparator.nullsLast(Comparator.naturalOrder())));
        List<LotRisk> result = new ArrayList<>();
        double absorbed = 0;
        for (Lot lot : ordered) {
            long days = Math.max(0, ChronoUnit.DAYS.between(asOf, lot.expiryDate()));
            double demandUntilExpiry = dailyRate * days - absorbed;
            double usable = Math.min(lot.quantity(), Math.max(0, demandUntilExpiry));
            absorbed += usable;
            int atRisk = (int) Math.ceil(lot.quantity() - usable - 1e-9);
            result.add(new LotRisk(lot.id(), lot.expiryDate(), lot.quantity(), Math.max(0, atRisk)));
        }
        return result;
    }
}
