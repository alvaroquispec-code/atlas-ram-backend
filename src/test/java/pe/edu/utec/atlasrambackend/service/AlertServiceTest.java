package pe.edu.utec.atlasrambackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.edu.utec.atlasrambackend.alert.OutbreakDetector;
import pe.edu.utec.atlasrambackend.dto.ResistanceCountRow;
import pe.edu.utec.atlasrambackend.event.EventPublisher;
import pe.edu.utec.atlasrambackend.event.ResistanceAlertEvent;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertServiceTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 4, 27);
    private static final Long KPN = 2L;

    private SusceptibilityResultRepository resultRepository;
    private ResistanceAlertRepository alertRepository;
    private EventPublisher eventPublisher;
    private AlertService service;
    private Facility hnhu;

    @BeforeEach
    void setUp() {
        resultRepository = mock(SusceptibilityResultRepository.class);
        alertRepository = mock(ResistanceAlertRepository.class);
        eventPublisher = mock(EventPublisher.class);
        AntibioticRepository antibioticRepository = mock(AntibioticRepository.class);
        MicroorganismRepository microorganismRepository = mock(MicroorganismRepository.class);

        hnhu = new Facility();
        hnhu.setId(6L);
        hnhu.setName("Hospital Nacional Hipólito Unanue");

        Microorganism kpn = new Microorganism();
        kpn.setId(KPN);
        kpn.setGenus("Klebsiella");
        kpn.setSpecies("pneumoniae");

        when(antibioticRepository.findAll()).thenReturn(List.of(
                antibiotic(10L, "IPM", "Imipenem", "Carbapenémico"),
                antibiotic(11L, "MEM", "Meropenem", "Carbapenémico")));
        when(microorganismRepository.findById(KPN)).thenReturn(Optional.of(kpn));
        when(alertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service = new AlertService(resultRepository, mock(IsolateRepository.class), mock(FacilityRepository.class),
                microorganismRepository, antibioticRepository, alertRepository,
                new OutbreakDetector(0.0005, 4, 20), eventPublisher, new AlertMapper(), 28, 365);
    }

    private static Antibiotic antibiotic(Long id, String code, String name, String antibioticClass) {
        Antibiotic a = new Antibiotic();
        a.setId(id);
        a.setCode(code);
        a.setName(name);
        a.setAntibioticClass(antibioticClass);
        return a;
    }

    /** Ventana con brote en imipenem y meropenem; línea base tranquila. */
    private void givenCarbapenemOutbreak() {
        LocalDate windowStart = AS_OF.minusDays(27);
        when(resultRepository.countByCombination(eq(6L), eq(windowStart), eq(AS_OF))).thenReturn(List.of(
                new ResistanceCountRow(KPN, 10L, 13, 9),
                new ResistanceCountRow(KPN, 11L, 13, 10)));
        when(resultRepository.countByCombination(eq(6L), eq(windowStart.minusDays(365)), eq(windowStart.minusDays(1))))
                .thenReturn(List.of(
                        new ResistanceCountRow(KPN, 10L, 64, 8),
                        new ResistanceCountRow(KPN, 11L, 64, 8)));
    }

    @Test
    void unBroteEnVariosCarbapenemicosGeneraUnaSolaAlerta() {
        givenCarbapenemOutbreak();
        when(alertRepository.findActive(6L, KPN, "Carbapenémico")).thenReturn(Optional.empty());

        List<ResistanceAlert> created = service.evaluate(hnhu, AS_OF);

        assertThat(created).hasSize(1);
        assertThat(created.get(0).getAntibioticClass()).isEqualTo("Carbapenémico");
        // Se queda con la señal más fuerte: meropenem (10 de 13) sobre imipenem (9 de 13).
        assertThat(created.get(0).getAntibiotic().getCode()).isEqualTo("MEM");
        assertThat(created.get(0).getDetectedOn()).isEqualTo(AS_OF);
        verify(eventPublisher, times(1)).publish(any(ResistanceAlertEvent.class));
    }

    @Test
    void siYaHayUnaAlertaActivaNoSeNotificaDeNuevo() {
        givenCarbapenemOutbreak();
        ResistanceAlert existing = new ResistanceAlert();
        existing.setStatus(AlertStatus.ACKNOWLEDGED);
        existing.setDetectedOn(AS_OF.minusWeeks(3));
        when(alertRepository.findActive(6L, KPN, "Carbapenémico")).thenReturn(Optional.of(existing));

        List<ResistanceAlert> created = service.evaluate(hnhu, AS_OF);

        assertThat(created).isEmpty();
        assertThat(existing.getResistantCount()).isEqualTo(10);
        // La fecha de detección no cambia aunque la alerta se actualice.
        assertThat(existing.getDetectedOn()).isEqualTo(AS_OF.minusWeeks(3));
        verify(alertRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void unaAlertaCerradaNoSePuedeReabrir() {
        ResistanceAlert closed = new ResistanceAlert();
        closed.setStatus(AlertStatus.CLOSED);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.updateStatus(1L, AlertStatus.OPEN))
                .isInstanceOf(BusinessRuleException.class);
    }

    /** Alerta activa de carbapenémicos en K. pneumoniae, con la ventana que termina en AS_OF. */
    private ResistanceAlert activeCarbapenemAlert() {
        Microorganism kpn = new Microorganism();
        kpn.setId(KPN);
        ResistanceAlert alert = new ResistanceAlert();
        alert.setId(33L);
        alert.setFacility(hnhu);
        alert.setMicroorganism(kpn);
        alert.setAntibioticClass("Carbapenémico");
        alert.setStatus(AlertStatus.OPEN);
        alert.setWindowEnd(AS_OF);
        alert.setResistantCount(15);
        when(alertRepository.findActiveByFacility(6L)).thenReturn(List.of(alert));
        return alert;
    }

    @Test
    void alRevertirLaCargaQueCausoLaAlertaSeRetiraConMotivo() {
        // Sin los datos revertidos, la ventana queda vacía: ya no hay señal.
        ResistanceAlert alert = activeCarbapenemAlert();

        List<ResistanceAlert> withdrawn = service.reassessAfterRevert(hnhu, 412L);

        assertThat(withdrawn).containsExactly(alert);
        assertThat(alert.getStatus()).isEqualTo(AlertStatus.CLOSED);
        assertThat(alert.getClosedAt()).isNotNull();
        assertThat(alert.getCloseReason()).contains("412");
    }

    @Test
    void siElBroteSigueSinLosDatosRevertidosLaAlertaSeQuedaConCifrasCorregidas() {
        givenCarbapenemOutbreak();
        ResistanceAlert alert = activeCarbapenemAlert();

        List<ResistanceAlert> withdrawn = service.reassessAfterRevert(hnhu, 412L);

        assertThat(withdrawn).isEmpty();
        assertThat(alert.getStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(alert.getResistantCount()).isEqualTo(10);
    }
}
