package pe.edu.utec.atlasrambackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.alert.OutbreakDetector;
import pe.edu.utec.atlasrambackend.alert.OutbreakSignal;
import pe.edu.utec.atlasrambackend.dto.AlertResponseDTO;
import pe.edu.utec.atlasrambackend.dto.ResistanceCountRow;
import pe.edu.utec.atlasrambackend.event.EventPublisher;
import pe.edu.utec.atlasrambackend.event.ResistanceAlertEvent;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.mapper.AlertMapper;
import pe.edu.utec.atlasrambackend.model.AlertStatus;
import pe.edu.utec.atlasrambackend.model.Antibiotic;
import pe.edu.utec.atlasrambackend.model.Facility;
import pe.edu.utec.atlasrambackend.model.Microorganism;
import pe.edu.utec.atlasrambackend.model.ResistanceAlert;
import pe.edu.utec.atlasrambackend.repository.AntibioticRepository;
import pe.edu.utec.atlasrambackend.repository.FacilityRepository;
import pe.edu.utec.atlasrambackend.repository.IsolateRepository;
import pe.edu.utec.atlasrambackend.repository.MicroorganismRepository;
import pe.edu.utec.atlasrambackend.repository.ResistanceAlertRepository;
import pe.edu.utec.atlasrambackend.repository.SusceptibilityResultRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Vigilancia de brotes por establecimiento.
 *
 * Antes: una ventana de 12 meses agregada por distrito y un umbral fijo de 60 %. Eso diluía un brote
 * de tres meses en un hospital y, a la vez, disparaba en cada carga por resistencias crónicas
 * (por ejemplo, A. baumannii frente a carbapenémicos). Ahora se compara una ventana corta con la
 * línea base del propio establecimiento y se deduplica: una alerta activa por establecimiento,
 * microorganismo y clase de antibiótico.
 */
@Service
public class AlertService {

    private final SusceptibilityResultRepository resultRepository;
    private final IsolateRepository isolateRepository;
    private final FacilityRepository facilityRepository;
    private final MicroorganismRepository microorganismRepository;
    private final AntibioticRepository antibioticRepository;
    private final ResistanceAlertRepository alertRepository;
    private final OutbreakDetector detector;
    private final EventPublisher eventPublisher;
    private final AlertMapper alertMapper;
    private final int windowDays;
    private final int baselineDays;

    public AlertService(SusceptibilityResultRepository resultRepository,
                        IsolateRepository isolateRepository,
                        FacilityRepository facilityRepository,
                        MicroorganismRepository microorganismRepository,
                        AntibioticRepository antibioticRepository,
                        ResistanceAlertRepository alertRepository,
                        OutbreakDetector detector,
                        EventPublisher eventPublisher,
                        AlertMapper alertMapper,
                        @Value("${app.alert.window-days:28}") int windowDays,
                        @Value("${app.alert.baseline-days:365}") int baselineDays) {
        this.resultRepository = resultRepository;
        this.isolateRepository = isolateRepository;
        this.facilityRepository = facilityRepository;
        this.microorganismRepository = microorganismRepository;
        this.antibioticRepository = antibioticRepository;
        this.alertRepository = alertRepository;
        this.detector = detector;
        this.eventPublisher = eventPublisher;
        this.alertMapper = alertMapper;
        this.windowDays = windowDays;
        this.baselineDays = baselineDays;
    }

    /**
     * Se llama al terminar una carga. Evalúa en la fecha de toma más reciente del establecimiento y no
     * en la fecha de hoy: un laboratorio puede subir datos con semanas de retraso.
     */
    @Transactional
    public void checkAfterUpload(Facility facility) {
        isolateRepository.findLatestCollectionDate(facility.getId())
                .ifPresent(latest -> evaluate(facility, latest));
    }

    /**
     * Repite la evaluación semana a semana en un periodo pasado. Sirve para la demo y para validar el
     * detector: muestra en qué fecha se habría disparado cada alerta.
     */
    @Transactional
    public List<AlertResponseDTO> replay(Long facilityId, LocalDate from, LocalDate to, int stepDays) {
        if (from.isAfter(to)) {
            throw new BusinessRuleException("La fecha inicial no puede ser posterior a la final");
        }
        if (stepDays < 1) {
            throw new BusinessRuleException("El paso debe ser de al menos 1 día");
        }
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Establecimiento", facilityId));
        List<ResistanceAlert> created = new ArrayList<>();
        for (LocalDate asOf = from; !asOf.isAfter(to); asOf = asOf.plusDays(stepDays)) {
            created.addAll(evaluate(facility, asOf));
        }
        return created.stream().map(alertMapper::toResponse).toList();
    }

    /** Evalúa todas las combinaciones del establecimiento en una fecha y devuelve las alertas nuevas. */
    List<ResistanceAlert> evaluate(Facility facility, LocalDate asOf) {
        LocalDate windowStart = asOf.minusDays(windowDays - 1L);
        LocalDate baselineEnd = windowStart.minusDays(1);
        LocalDate baselineStart = baselineEnd.minusDays(baselineDays - 1L);

        Map<String, ResistanceCountRow> baseline = resultRepository
                .countByCombination(facility.getId(), baselineStart, baselineEnd).stream()
                .collect(Collectors.toMap(AlertService::key, Function.identity()));
        List<ResistanceCountRow> window = resultRepository.countByCombination(facility.getId(), windowStart, asOf);

        Map<Long, Antibiotic> antibiotics = antibioticRepository.findAll().stream()
                .collect(Collectors.toMap(Antibiotic::getId, Function.identity()));

        // Una clase de antibiótico puede dar señal en varios fármacos a la vez (una carbapenemasa hace
        // resistentes a imipenem, meropenem y ertapenem). Se agrupan y se conserva la señal más fuerte.
        Map<String, Candidate> strongest = new LinkedHashMap<>();
        for (ResistanceCountRow row : window) {
            ResistanceCountRow base = baseline.get(key(row));
            if (base == null) {
                continue;
            }
            detector.evaluate(row.tested(), row.resistant(), base.tested(), base.resistant())
                    .ifPresent(signal -> {
                        Antibiotic antibiotic = antibiotics.get(row.antibioticId());
                        String antibioticClass = classOf(antibiotic);
                        Candidate candidate = new Candidate(row, base, antibiotic, antibioticClass, signal);
                        strongest.merge(row.microorganismId() + "|" + antibioticClass, candidate,
                                (a, b) -> a.signal().pValue() <= b.signal().pValue() ? a : b);
                    });
        }

        List<ResistanceAlert> created = new ArrayList<>();
        for (Candidate c : strongest.values()) {
            var active = alertRepository.findActive(facility.getId(), c.row().microorganismId(), c.antibioticClass());
            if (active.isPresent()) {
                // Deduplicación: el brote ya está avisado. Se actualizan las cifras, sin notificar de nuevo.
                apply(active.get(), c, windowStart, asOf);
                continue;
            }
            Microorganism microorganism = microorganismRepository.findById(c.row().microorganismId())
                    .orElseThrow(() -> new ResourceNotFoundException("Microorganismo", c.row().microorganismId()));
            ResistanceAlert alert = new ResistanceAlert();
            alert.setFacility(facility);
            alert.setMicroorganism(microorganism);
            alert.setAntibioticClass(c.antibioticClass());
            apply(alert, c, windowStart, asOf);
            ResistanceAlert saved = alertRepository.save(alert);
            created.add(saved);
            eventPublisher.publish(new ResistanceAlertEvent(
                    facility.getId(),
                    facility.getName(),
                    microorganism.getGenus() + " " + (microorganism.getSpecies() == null ? "" : microorganism.getSpecies()),
                    c.antibiotic().getName(),
                    c.signal().observedRate() * 100.0,
                    c.row().tested()));
        }
        return created;
    }

    @Transactional(readOnly = true)
    public List<AlertResponseDTO> findAll(AlertStatus status, Collection<Long> allowedFacilityIds) {
        List<ResistanceAlert> alerts;
        if (allowedFacilityIds == null) {
            alerts = alertRepository.findAllWithDetails(status);
        } else if (allowedFacilityIds.isEmpty()) {
            alerts = List.of();
        } else {
            alerts = alertRepository.findForFacilitiesWithDetails(status, allowedFacilityIds);
        }
        return alerts.stream().map(alertMapper::toResponse).toList();
    }

    /** Transiciones válidas: OPEN → ACKNOWLEDGED → CLOSED, u OPEN → CLOSED. Una alerta cerrada no se reabre. */
    @Transactional
    public AlertResponseDTO updateStatus(Long id, AlertStatus newStatus) {
        ResistanceAlert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alerta", id));
        AlertStatus current = alert.getStatus();
        boolean valid = (current == AlertStatus.OPEN && newStatus != AlertStatus.OPEN)
                || (current == AlertStatus.ACKNOWLEDGED && newStatus == AlertStatus.CLOSED);
        if (!valid) {
            throw new BusinessRuleException("No se puede pasar una alerta de " + current + " a " + newStatus);
        }
        alert.setStatus(newStatus);
        alert.setUpdatedAt(Instant.now());
        if (newStatus == AlertStatus.CLOSED) {
            alert.setClosedAt(Instant.now());
        }
        return alertMapper.toResponse(alert);
    }

    private void apply(ResistanceAlert alert, Candidate c, LocalDate windowStart, LocalDate windowEnd) {
        alert.setAntibiotic(c.antibiotic());
        alert.setWindowStart(windowStart);
        alert.setWindowEnd(windowEnd);
        alert.setTestedCount((int) c.row().tested());
        alert.setResistantCount((int) c.row().resistant());
        alert.setBaselineTested((int) c.base().tested());
        alert.setBaselineResistant((int) c.base().resistant());
        alert.setPValue(c.signal().pValue());
        alert.setUpdatedAt(Instant.now());
    }

    private static String key(ResistanceCountRow row) {
        return row.microorganismId() + "|" + row.antibioticId();
    }

    /** Sin clase registrada, el antibiótico forma su propio grupo. */
    private static String classOf(Antibiotic antibiotic) {
        String antibioticClass = antibiotic.getAntibioticClass();
        return antibioticClass == null || antibioticClass.isBlank() ? antibiotic.getCode() : antibioticClass;
    }

    private record Candidate(ResistanceCountRow row, ResistanceCountRow base, Antibiotic antibiotic,
                             String antibioticClass, OutbreakSignal signal) {
    }
}
