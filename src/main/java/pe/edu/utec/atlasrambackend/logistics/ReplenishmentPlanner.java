package pe.edu.utec.atlasrambackend.logistics;

/**
 * Política de revisión periódica (R, S) para un ítem: cada R días se revisa y se pide lo necesario para
 * subir la posición de inventario hasta S. S cubre la demanda del intervalo de protección R + L (lo que
 * se consumirá hasta que llegue el SIGUIENTE pedido) con la probabilidad del nivel de servicio.
 *
 * Se eligió (R, S) y no (s, Q) porque en las farmacias la reposición se programa por periodos (el SISMED
 * consolida el consumo y el stock mes a mes), no con revisión continua. Ver Silver, Pyke y Thomas,
 * Inventory and Production Management in Supply Chains (4.ª ed., 2017).
 */
public final class ReplenishmentPlanner {

    public static final double DAYS_PER_MONTH = 365.25 / 12;

    /**
     * Piso del stock de seguridad: al menos el 10 % de la demanda esperada en R + L, aunque el bootstrap
     * diga que basta menos (demanda muy estable). Es la práctica que sugirió un patólogo clínico jefe de
     * laboratorio de EsSalud consultado para el proyecto: ante cualquier imprevisto, la farmacia debería
     * tener al menos un 10 % por encima de su consumo.
     */
    public static final double MIN_SAFETY_FRACTION = 0.10;

    private ReplenishmentPlanner() {
    }

    public record Input(int[] series, int available, int onOrder, int reviewPeriodDays, int leadTimeDays,
                        double serviceLevel, long seed) {
    }

    public record Plan(DemandClassifier.Result classification, ForecastMethod method,
                       Double forecastPerMonth, double dailyRate, Double coverageDays,
                       int protectionDays, Double expectedProtectionDemand, Integer safetyStock,
                       Integer orderUpTo, int inventoryPosition, Integer suggestedOrder,
                       boolean safetyFloorApplied) {
    }

    public static Plan plan(Input in) {
        int[] series = in.series();
        DemandClassifier.Result classification = DemandClassifier.classify(series);
        double dailyRate = dailyRate(series);
        // Sin ningún consumo en los últimos 6 meses (SISMED: sin rotación) no se sugiere reponer, aunque la
        // historia más antigua todavía permita pronosticar: el ítem se dejó de usar.
        ForecastMethod method = dailyRate == 0 ? ForecastMethod.NONE
                : Forecasting.choose(classification.pattern(), series);
        Double coverage = dailyRate > 0 ? in.available() / dailyRate : null;
        int protectionDays = in.reviewPeriodDays() + in.leadTimeDays();
        int position = in.available() + in.onOrder();

        if (method == ForecastMethod.NONE) {
            return new Plan(classification, method, null, dailyRate, coverage, protectionDays,
                    null, null, null, position, null, false);
        }
        double forecast = Forecasting.forecast(method, series);
        double horizon = protectionDays / DAYS_PER_MONTH;
        DemandBootstrap.Result simulated = DemandBootstrap.simulate(
                series, horizon, in.serviceLevel(), DemandBootstrap.DRAWS, in.seed());
        double expected = forecast * horizon;
        double floor = expected * (1 + MIN_SAFETY_FRACTION);
        boolean floorApplied = floor > simulated.quantile();
        int orderUpTo = (int) Math.ceil(Math.max(simulated.quantile(), floor));
        int safetyStock = (int) Math.max(0, Math.ceil(orderUpTo - expected));
        int suggested = Math.max(0, orderUpTo - position);
        return new Plan(classification, method, forecast, dailyRate, coverage, protectionDays,
                expected, safetyStock, orderUpTo, position, suggested, floorApplied);
    }

    /**
     * Consumo diario de los últimos 6 meses INCLUYENDO los meses en cero. A diferencia del CPMA, no
     * supone que un mes en cero fue un mes sin stock; es la tasa adecuada para estimar cobertura y
     * vencimientos de ítems intermitentes.
     */
    public static double dailyRate(int[] series) {
        int from = Math.max(0, series.length - SismedIndicators.WINDOW_MONTHS);
        int months = series.length - from;
        if (months == 0) {
            return 0;
        }
        long total = 0;
        for (int i = from; i < series.length; i++) {
            total += series[i];
        }
        return total / (months * DAYS_PER_MONTH);
    }
}
