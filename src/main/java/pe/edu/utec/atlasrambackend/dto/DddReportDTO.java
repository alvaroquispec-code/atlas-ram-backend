package pe.edu.utec.atlasrambackend.dto;

import pe.edu.utec.atlasrambackend.model.AwareCategory;

import java.time.LocalDate;
import java.util.List;

/** bedDaysSource: REQUEST (enviado en la consulta), FACILITY (camas × ocupación × días) o UNAVAILABLE. */
public record DddReportDTO(
        Long facilityId,
        LocalDate from,
        LocalDate to,
        Double occupiedBedDays,
        String bedDaysSource,
        double totalDdd,
        Double dddPer100BedDays,
        List<AwareGroup> byAware,
        Double accessShare,
        double whoAccessTarget,
        List<String> itemsWithoutDdd,
        List<String> itemsWithoutAware,
        String method
) {
    public record AwareGroup(AwareCategory awareCategory, double ddd, Double dddPer100BedDays, double share) {
    }
}
