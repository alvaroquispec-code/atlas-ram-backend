package pe.edu.utec.atlasrambackend.report;

import org.junit.jupiter.api.Test;
import pe.edu.utec.atlasrambackend.dto.AntibiogramResponseDTO;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.model.Facility;
import pe.edu.utec.atlasrambackend.model.Microorganism;
import pe.edu.utec.atlasrambackend.model.ResistanceAlert;
import pe.edu.utec.atlasrambackend.repository.FacilityRepository;
import pe.edu.utec.atlasrambackend.repository.ResistanceAlertRepository;
import pe.edu.utec.atlasrambackend.service.AntibiogramService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProaReportServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 6, 30);

    private final FacilityRepository facilityRepository = mock(FacilityRepository.class);
    private final ResistanceAlertRepository alertRepository = mock(ResistanceAlertRepository.class);
    private final AntibiogramService antibiogramService = mock(AntibiogramService.class);
    private final ProaReportService service =
            new ProaReportService(facilityRepository, alertRepository, antibiogramService);

    @Test
    void generaUnPdfConAntibiogramaYAlertas() {
        Facility hnhu = new Facility();
        hnhu.setId(6L);
        hnhu.setName("Hospital Nacional Hipólito Unanue");
        when(facilityRepository.findById(6L)).thenReturn(Optional.of(hnhu));
        when(antibiogramService.calculateForFacility(6L, FROM, TO)).thenReturn(List.of(
                new AntibiogramResponseDTO("Klebsiella pneumoniae", "Meropenem", 120, 30, 25.0, 2L, 11L, 88, 2,
                        73.3, 64.8, 80.4, 18.1, 33.4, true, 4, 90.0, 0.0, null),
                new AntibiogramResponseDTO("Escherichia coli", "Amikacina", 12, 0, 0.0, 1L, 13L, 12, 0,
                        100.0, 75.8, 100.0, 0.0, 24.2, false, 0, 90.0, 0.0, null)));
        Microorganism kpn = new Microorganism();
        kpn.setGenus("Klebsiella");
        kpn.setSpecies("pneumoniae");
        ResistanceAlert alert = new ResistanceAlert();
        alert.setMicroorganism(kpn);
        alert.setAntibioticClass("Carbapenémico");
        alert.setDetectedOn(LocalDate.of(2026, 4, 27));
        alert.setWindowStart(LocalDate.of(2026, 3, 30));
        alert.setWindowEnd(LocalDate.of(2026, 4, 27));
        alert.setTestedCount(14);
        alert.setResistantCount(9);
        alert.setBaselineTested(80);
        alert.setBaselineResistant(8);
        alert.setPValue(0.00002);
        when(alertRepository.findActiveByFacility(6L)).thenReturn(List.of(alert));

        byte[] pdf = service.generate(6L, FROM, TO);

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1000);
    }

    @Test
    void establecimientoInexistente() {
        when(facilityRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.generate(99L, FROM, TO)).isInstanceOf(ResourceNotFoundException.class);
    }
}
