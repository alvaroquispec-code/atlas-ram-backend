package pe.edu.utec.atlasrambackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pe.edu.utec.atlasrambackend.dto.AntibiogramResponseDTO;
import pe.edu.utec.atlasrambackend.event.EventPublisher;
import pe.edu.utec.atlasrambackend.event.ResistanceAlertEvent;
import pe.edu.utec.atlasrambackend.model.Facility;
import pe.edu.utec.atlasrambackend.repository.SusceptibilityResultRepository;

import java.time.LocalDate;
import java.util.List;

@Service
public class AlertService {

    private final SusceptibilityResultRepository resultRepository;
    private final EventPublisher eventPublisher;
    private final double threshold;

    public AlertService(SusceptibilityResultRepository resultRepository,
                        EventPublisher eventPublisher,
                        @Value("${app.alert.resistance-threshold:60.0}") double threshold) {
        this.resultRepository = resultRepository;
        this.eventPublisher = eventPublisher;
        this.threshold = threshold;
    }

    public void checkAfterUpload(Facility facility) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(12);

        List<AntibiogramResponseDTO> antibiogram =
                resultRepository.calculateAntibiogram(
                        null, facility.getDistrict().getId(), from, to);

        for (AntibiogramResponseDTO row : antibiogram) {
            if (row.resistancePercentage() >= threshold) {
                eventPublisher.publish(new ResistanceAlertEvent(
                        facility.getId(),
                        facility.getName(),
                        row.microorganismName(),
                        row.antibioticName(),
                        row.resistancePercentage(),
                        row.totalTested()));
            }
        }
    }
}
