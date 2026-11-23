package pe.edu.utec.atlasrambackend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.dto.DddReportDTO;
import pe.edu.utec.atlasrambackend.dto.FacilityStockReportDTO;
import pe.edu.utec.atlasrambackend.dto.StockItemStatusDTO;
import pe.edu.utec.atlasrambackend.dto.StockLotResponseDTO;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.logistics.AbcClass;
import pe.edu.utec.atlasrambackend.logistics.AbcVenAnalysis;
import pe.edu.utec.atlasrambackend.logistics.DddIndicator;
import pe.edu.utec.atlasrambackend.logistics.ExpiryRisk;
import pe.edu.utec.atlasrambackend.logistics.OutbreakPressure;
import pe.edu.utec.atlasrambackend.logistics.ReplenishmentPlanner;
import pe.edu.utec.atlasrambackend.logistics.ServiceLevelPolicy;
import pe.edu.utec.atlasrambackend.logistics.SismedIndicators;
import pe.edu.utec.atlasrambackend.logistics.StockState;
import pe.edu.utec.atlasrambackend.model.AwareCategory;
import pe.edu.utec.atlasrambackend.model.Facility;
import pe.edu.utec.atlasrambackend.model.Microorganism;
import pe.edu.utec.atlasrambackend.model.StockItem;
import pe.edu.utec.atlasrambackend.model.StockLot;
import pe.edu.utec.atlasrambackend.repository.FacilityRepository;
import pe.edu.utec.atlasrambackend.repository.LogisticsQueries;
import pe.edu.utec.atlasrambackend.repository.ResistanceAlertRepository;
import pe.edu.utec.atlasrambackend.repository.StockItemRepository;
import pe.edu.utec.atlasrambackend.repository.StockLotRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tablero de inventario de antibióticos de un establecimiento y su indicador de consumo DDD.
 * Ver el paquete logistics para cada método y su fuente.
 */
@Service
public class LogisticsService {

    static final int HISTORY_MONTHS = 12;
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final long SEED = 2031;
    private static final int MAX_DDD_DAYS = 3 * 366;

    static final String METHOD = """
            Disponibilidad: indicadores SISMED (DIGEMID). CPMA = consumo de los últimos 6 meses completos ÷ meses con \
            consumo; MED = stock disponible no vencido ÷ CPMA; desabastecido (stock 0), substock (MED < 2), \
            normostock (2 a 6), sobrestock (> 6), sin rotación (stock sin consumo). \
            Demanda: clasificación de Syntetos-Boylan-Croston (ADI 1.32, CV² 0.49) sobre hasta 12 meses; pronóstico \
            SES (frecuente), SBA (intermitente) o TSB (intermitente que se apaga). \
            Reposición (R, S): S = percentil del nivel de servicio de la demanda en R + L días, estimado por el \
            bootstrap de Willemain (5 000 simulaciones); pedido sugerido = S − (disponible + en camino). \
            Nivel de servicio: 99 % vital o Reserve, 95 % esencial o Watch, 90 % el resto, salvo valor del ítem. \
            ABC por valor de consumo de 12 meses cruzado con VEN. Riesgo de vencimiento: saldo de cada lote que no \
            se consumirá antes de vencer al ritmo actual (FEFO). Presión de brote: alerta activa + fármaco de \
            escalamiento de otra clase + cobertura menor a 1.5 × (R + L).""";

    private final StockItemRepository itemRepository;
    private final StockLotRepository lotRepository;
    private final FacilityRepository facilityRepository;
    private final ResistanceAlertRepository alertRepository;
    private final LogisticsQueries queries;

    public LogisticsService(StockItemRepository itemRepository, StockLotRepository lotRepository,
                            FacilityRepository facilityRepository, ResistanceAlertRepository alertRepository,
                            LogisticsQueries queries) {
        this.itemRepository = itemRepository;
        this.lotRepository = lotRepository;
        this.facilityRepository = facilityRepository;
        this.alertRepository = alertRepository;
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    public FacilityStockReportDTO report(Long facilityId) {
        return report(facilityId, LocalDate.now(LIMA));
    }

    /** today: el mes en curso está incompleto y no entra en las series; el stock es el actual. */
    @Transactional(readOnly = true)
    public FacilityStockReportDTO report(Long facilityId, LocalDate today) {
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Establecimiento", facilityId));
        List<StockItem> items = itemRepository.findByFacilityIdAndActiveTrueOrderByIdAsc(facilityId);

        LocalDate currentMonth = today.withDayOfMonth(1);
        LocalDate windowStart = currentMonth.minusMonths(HISTORY_MONTHS);
        Map<Long, int[]> fullSeries = new HashMap<>();
        for (LogisticsQueries.MonthlyRow row : queries.monthlyConsumption(facilityId, windowStart, currentMonth)) {
            int index = (int) ChronoUnit.MONTHS.between(windowStart, row.month());
            fullSeries.computeIfAbsent(row.itemId(), k -> new int[HISTORY_MONTHS])[index] += row.units();
        }
        Map<Long, LocalDate> firstMovement = new HashMap<>();
        for (LogisticsQueries.FirstMovementRow row : queries.firstMovements(facilityId)) {
            firstMovement.put(row.itemId(), row.firstDate());
        }
        Map<Long, List<StockLot>> lotsByItem = new HashMap<>();
        for (StockLot lot : lotRepository.findOnHandByFacility(facilityId)) {
            lotsByItem.computeIfAbsent(lot.getStockItem().getId(), k -> new ArrayList<>()).add(lot);
        }
        List<OutbreakPressure.ActiveAlert> alerts = alertRepository.findActiveByFacility(facilityId).stream()
                .map(a -> new OutbreakPressure.ActiveAlert(organismName(a.getMicroorganism()), a.getAntibioticClass()))
                .toList();

        Map<Long, Double> annualValue = new HashMap<>();
        for (StockItem item : items) {
            if (item.getUnitCost() != null) {
                int units = sum(fullSeries.getOrDefault(item.getId(), new int[HISTORY_MONTHS]));
                annualValue.put(item.getId(), units * item.getUnitCost().doubleValue());
            }
        }
        Map<Long, AbcClass> abc = AbcVenAnalysis.classify(annualValue);

        List<StockItemStatusDTO> statuses = new ArrayList<>();
        for (StockItem item : items) {
            int[] series = activeSeries(fullSeries.get(item.getId()), firstMovement.get(item.getId()),
                    windowStart, currentMonth);
            statuses.add(status(item, series, windowStart, lotsByItem.getOrDefault(item.getId(), List.of()),
                    abc.get(item.getId()), alerts, today));
        }
        return summarize(facility, today, statuses, alerts.size());
    }

    @Transactional(readOnly = true)
    public List<StockLotResponseDTO> lots(Long itemId) {
        StockItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Ítem de inventario", itemId));
        LocalDate today = LocalDate.now(LIMA);
        LocalDate currentMonth = today.withDayOfMonth(1);
        LocalDate windowStart = currentMonth.minusMonths(HISTORY_MONTHS);
        Long facilityId = item.getFacility().getId();
        int[] full = new int[HISTORY_MONTHS];
        for (LogisticsQueries.MonthlyRow row : queries.monthlyConsumption(facilityId, windowStart, currentMonth)) {
            if (row.itemId() == itemId) {
                full[(int) ChronoUnit.MONTHS.between(windowStart, row.month())] += row.units();
            }
        }
        LocalDate first = queries.firstMovements(facilityId).stream()
                .filter(r -> r.itemId() == itemId).map(LogisticsQueries.FirstMovementRow::firstDate)
                .findFirst().orElse(null);
        double rate = ReplenishmentPlanner.dailyRate(activeSeries(full, first, windowStart, currentMonth));

        List<StockLot> lots = lotRepository.findOnHandByItem(itemId);
        Map<Long, Integer> risk = new HashMap<>();
        for (ExpiryRisk.LotRisk r : ExpiryRisk.evaluate(usableLots(lots, today), rate, today)) {
            risk.put(r.lotId(), r.unitsAtRisk());
        }
        return lots.stream().map(l -> new StockLotResponseDTO(l.getId(), itemId, l.getLotNumber(),
                l.getExpiryDate(), l.getQuantityReceived(), l.getQuantityOnHand(), l.getReceivedOn(),
                l.getExpiryDate().isBefore(today), risk.get(l.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public DddReportDTO ddd(Long facilityId, LocalDate from, LocalDate to, Double occupiedBedDays) {
        if (to.isBefore(from)) {
            throw new BusinessRuleException("La fecha final no puede ser anterior a la inicial");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_DDD_DAYS) {
            throw new BusinessRuleException("El periodo del indicador DDD no puede superar 3 años");
        }
        if (occupiedBedDays != null && occupiedBedDays <= 0) {
            throw new BusinessRuleException("Los días-cama deben ser positivos");
        }
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Establecimiento", facilityId));
        String source = "REQUEST";
        Double bedDays = occupiedBedDays;
        if (bedDays == null) {
            if (facility.getBeds() != null && facility.getOccupancyRate() != null) {
                bedDays = facility.getBeds() * facility.getOccupancyRate().doubleValue() * days;
                source = "FACILITY";
            } else {
                source = "UNAVAILABLE";
            }
        }
        List<DddIndicator.Consumption> rows = queries.dddConsumption(facilityId, from, to).stream()
                .map(r -> new DddIndicator.Consumption(
                        r.code() + " " + r.presentation(),
                        r.awareCategory() == null ? null : AwareCategory.valueOf(r.awareCategory()),
                        r.units() * r.gramsPerUnit().doubleValue(),
                        r.dddGrams() == null ? null : r.dddGrams().doubleValue()))
                .toList();
        DddIndicator.Result result = DddIndicator.compute(rows, bedDays);
        String method = """
                DDD = gramos dispensados ÷ DDD de la OMS para la vía de la presentación (índice ATC/DDD). \
                Indicador = DDD × 100 ÷ días-cama ocupados. Días-cama: valor enviado o camas × ocupación × días \
                del periodo. Meta OMS de ≥ 60 % de consumo Access: es nacional; en un hospital es referencia.""";
        return new DddReportDTO(facilityId, from, to, bedDays, source, round(result.totalDdd(), 1),
                round(result.dddPer100BedDays(), 2),
                result.byAware().stream().map(g -> new DddReportDTO.AwareGroup(g.aware(), round(g.ddd(), 1),
                        round(g.dddPer100BedDays(), 2), round(g.share(), 4))).toList(),
                round(result.accessShare(), 4), DddIndicator.WHO_ACCESS_TARGET,
                result.withoutDdd(), result.withoutAware(), method);
    }

    private StockItemStatusDTO status(StockItem item, int[] series, LocalDate windowStart, List<StockLot> lots,
                                      AbcClass abc, List<OutbreakPressure.ActiveAlert> alerts, LocalDate today) {
        int available = 0;
        int expired = 0;
        for (StockLot lot : lots) {
            if (lot.getExpiryDate().isBefore(today)) {
                expired += lot.getQuantityOnHand();
            } else {
                available += lot.getQuantityOnHand();
            }
        }
        SismedIndicators.Result sismed = SismedIndicators.compute(series, available);
        AwareCategory aware = item.getAntibiotic().getAwareCategory();
        double serviceLevel = ServiceLevelPolicy.target(item.getVenClass(), aware, item.getServiceLevel());
        ReplenishmentPlanner.Plan plan = ReplenishmentPlanner.plan(new ReplenishmentPlanner.Input(
                series, available, item.getQuantityOnOrder(), item.getReviewPeriodDays(),
                item.getLeadTimeDays(), serviceLevel, SEED + item.getId()));

        int atRisk = ExpiryRisk.evaluate(usableLots(lots, today), plan.dailyRate(), today).stream()
                .mapToInt(ExpiryRisk.LotRisk::unitsAtRisk).sum();
        LocalDate nextExpiry = lots.stream().map(StockLot::getExpiryDate).filter(d -> !d.isBefore(today))
                .min(LocalDate::compareTo).orElse(null);
        Optional<String> outbreak = OutbreakPressure.evaluate(alerts, item.getAntibiotic().getCode(),
                item.getAntibiotic().getAntibioticClass(), available, plan.coverageDays(), plan.protectionDays());

        List<StockItemStatusDTO.MonthlyConsumption> monthly = new ArrayList<>();
        LocalDate firstMonth = windowStart.plusMonths(HISTORY_MONTHS - series.length);
        for (int i = 0; i < series.length; i++) {
            monthly.add(new StockItemStatusDTO.MonthlyConsumption(firstMonth.plusMonths(i), series[i]));
        }
        BigDecimal unitCost = item.getUnitCost();
        return new StockItemStatusDTO(
                item.getId(), item.getAntibiotic().getId(), item.getAntibiotic().getCode(),
                item.getAntibiotic().getName(), aware, item.getPresentation(), item.getVenClass(), abc,
                AbcVenAnalysis.category(abc, item.getVenClass()), unitCost,
                available, expired, item.getQuantityOnOrder(), round(sismed.cpma(), 1),
                round(sismed.monthsOfStock(), 2), sismed.state(), sismed.state().label(),
                round(plan.dailyRate(), 2), round(plan.coverageDays(), 1),
                plan.classification().pattern(), round(plan.classification().adi(), 2),
                round(plan.classification().cv2(), 2), plan.method(), round(plan.forecastPerMonth(), 1),
                series.length,
                item.getReviewPeriodDays(), item.getLeadTimeDays(), serviceLevel,
                round(plan.expectedProtectionDemand(), 1), plan.safetyStock(), plan.orderUpTo(),
                plan.inventoryPosition(), plan.suggestedOrder(), money(unitCost, plan.suggestedOrder()),
                atRisk, money(unitCost, atRisk), nextExpiry, outbreak.isPresent(), outbreak.orElse(null),
                monthly);
    }

    private FacilityStockReportDTO summarize(Facility facility, LocalDate today, List<StockItemStatusDTO> statuses,
                                             int activeAlerts) {
        Map<StockState, Long> byState = new EnumMap<>(StockState.class);
        for (StockState state : StockState.values()) {
            byState.put(state, 0L);
        }
        BigDecimal orderValue = BigDecimal.ZERO;
        BigDecimal riskValue = BigDecimal.ZERO;
        int toOrder = 0;
        int withRisk = 0;
        int underPressure = 0;
        for (StockItemStatusDTO s : statuses) {
            byState.merge(s.sismedState(), 1L, Long::sum);
            if (s.suggestedOrder() != null && s.suggestedOrder() > 0) {
                toOrder++;
                if (s.suggestedOrderValue() != null) {
                    orderValue = orderValue.add(s.suggestedOrderValue());
                }
            }
            if (s.unitsAtRiskOfExpiry() > 0) {
                withRisk++;
                if (s.valueAtRiskOfExpiry() != null) {
                    riskValue = riskValue.add(s.valueAtRiskOfExpiry());
                }
            }
            if (s.outbreakPressure()) {
                underPressure++;
            }
        }
        return new FacilityStockReportDTO(facility.getId(), facility.getName(), today, statuses.size(), byState,
                toOrder, orderValue, withRisk, riskValue, underPressure, activeAlerts, statuses, METHOD);
    }

    /**
     * Serie desde el primer mes con movimiento (ingreso o consumo) dentro de la ventana de 12 meses: los meses
     * anteriores a que el ítem existiera no son "meses sin demanda" y distorsionarían el ADI.
     */
    static int[] activeSeries(int[] full, LocalDate firstMovement, LocalDate windowStart, LocalDate currentMonth) {
        if (firstMovement == null) {
            return new int[0];
        }
        LocalDate firstMonth = firstMovement.withDayOfMonth(1);
        if (!firstMonth.isBefore(currentMonth)) {
            return new int[0];
        }
        int start = firstMonth.isBefore(windowStart) ? 0 : (int) ChronoUnit.MONTHS.between(windowStart, firstMonth);
        int[] source = full == null ? new int[HISTORY_MONTHS] : full;
        int[] series = new int[HISTORY_MONTHS - start];
        System.arraycopy(source, start, series, 0, series.length);
        return series;
    }

    private static String organismName(Microorganism m) {
        return m.getSpecies() == null || m.getSpecies().isBlank() ? m.getGenus() : m.getGenus() + " " + m.getSpecies();
    }

    private static List<ExpiryRisk.Lot> usableLots(List<StockLot> lots, LocalDate today) {
        return lots.stream().filter(l -> !l.getExpiryDate().isBefore(today))
                .map(l -> new ExpiryRisk.Lot(l.getId(), l.getExpiryDate(), l.getQuantityOnHand())).toList();
    }

    private static int sum(int[] values) {
        int total = 0;
        for (int v : values) {
            total += v;
        }
        return total;
    }

    private static BigDecimal money(BigDecimal unitCost, Integer units) {
        return unitCost == null || units == null ? null
                : unitCost.multiply(BigDecimal.valueOf(units)).setScale(2, RoundingMode.HALF_UP);
    }

    private static Double round(Double value, int decimals) {
        if (value == null) {
            return null;
        }
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
