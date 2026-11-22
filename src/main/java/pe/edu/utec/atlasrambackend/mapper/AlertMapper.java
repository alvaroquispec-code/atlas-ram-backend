package pe.edu.utec.atlasrambackend.mapper;

import org.springframework.stereotype.Component;
import pe.edu.utec.atlasrambackend.dto.AlertResponseDTO;
import pe.edu.utec.atlasrambackend.model.ResistanceAlert;

@Component
public class AlertMapper {

    public AlertResponseDTO toResponse(ResistanceAlert a) {
        return new AlertResponseDTO(
                a.getId(),
                a.getFacility().getId(),
                a.getFacility().getName(),
                a.getMicroorganism().getGenus() + " "
                        + (a.getMicroorganism().getSpecies() == null ? "" : a.getMicroorganism().getSpecies()),
                a.getAntibiotic().getName(),
                a.getAntibioticClass(),
                a.getStatus(),
                a.getDetectedOn(),
                a.getWindowStart(),
                a.getWindowEnd(),
                a.getTestedCount(),
                a.getResistantCount(),
                percentage(a.getResistantCount(), a.getTestedCount()),
                a.getBaselineTested(),
                a.getBaselineResistant(),
                percentage(a.getBaselineResistant(), a.getBaselineTested()),
                a.getPValue(),
                a.getCreatedAt(),
                a.getUpdatedAt());
    }

    private static double percentage(int part, int total) {
        return total == 0 ? 0.0 : part * 100.0 / total;
    }
}
