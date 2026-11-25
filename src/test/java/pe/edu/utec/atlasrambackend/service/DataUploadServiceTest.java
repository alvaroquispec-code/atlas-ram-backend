package pe.edu.utec.atlasrambackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.edu.utec.atlasrambackend.dto.UploadRevertResponseDTO;
import pe.edu.utec.atlasrambackend.exception.UploadNotRevertableException;
import pe.edu.utec.atlasrambackend.mapper.DataUploadMapper;
import pe.edu.utec.atlasrambackend.model.DataUpload;
import pe.edu.utec.atlasrambackend.model.Facility;
import pe.edu.utec.atlasrambackend.model.ResistanceAlert;
import pe.edu.utec.atlasrambackend.model.UploadStatus;
import pe.edu.utec.atlasrambackend.model.User;
import pe.edu.utec.atlasrambackend.repository.DataUploadRepository;
import pe.edu.utec.atlasrambackend.repository.IsolateRepository;
import pe.edu.utec.atlasrambackend.repository.SusceptibilityResultRepository;
import pe.edu.utec.atlasrambackend.repository.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataUploadServiceTest {

    private static final String ADMIN = "admin@atlasram.test";

    private DataUploadRepository uploadRepository;
    private IsolateRepository isolateRepository;
    private SusceptibilityResultRepository resultRepository;
    private AlertService alertService;
    private DataUploadService service;
    private Facility hnch;
    private User admin;

    @BeforeEach
    void setUp() {
        uploadRepository = mock(DataUploadRepository.class);
        isolateRepository = mock(IsolateRepository.class);
        resultRepository = mock(SusceptibilityResultRepository.class);
        alertService = mock(AlertService.class);
        UserRepository userRepository = mock(UserRepository.class);

        hnch = new Facility();
        hnch.setId(1L);
        admin = new User();
        admin.setFullName("Administrador");
        when(userRepository.findByEmail(ADMIN)).thenReturn(Optional.of(admin));

        service = new DataUploadService(uploadRepository, isolateRepository, resultRepository,
                userRepository, alertService, new DataUploadMapper());
    }

    private DataUpload upload(Long id, UploadStatus status) {
        DataUpload u = new DataUpload();
        u.setId(id);
        u.setFileName("prueba.csv");
        u.setStatus(status);
        u.setFacility(hnch);
        when(uploadRepository.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void revertirBorraResultadosYAislamientosYDejaAuditoria() {
        DataUpload u = upload(412L, UploadStatus.COMPLETED);
        when(resultRepository.deleteByUploadId(412L)).thenReturn(7);
        when(isolateRepository.deleteByUploadId(412L)).thenReturn(1);
        ResistanceAlert falsa = new ResistanceAlert();
        falsa.setId(33L);
        when(alertService.reassessAfterRevert(hnch, 412L)).thenReturn(List.of(falsa));

        UploadRevertResponseDTO r = service.revert(412L, "  CSV de otro hospital  ", ADMIN);

        assertThat(r.resultsRemoved()).isEqualTo(7);
        assertThat(r.isolatesRemoved()).isEqualTo(1);
        assertThat(r.withdrawnAlertIds()).containsExactly(33L);
        assertThat(u.getStatus()).isEqualTo(UploadStatus.REVERTED);
        assertThat(u.getRevertedBy()).isSameAs(admin);
        assertThat(u.getRevertReason()).isEqualTo("CSV de otro hospital");
        assertThat(r.upload().revertedBy()).isEqualTo("Administrador");

        // Los resultados se borran antes que los aislamientos (llave foránea) y las alertas se revisan al final.
        var order = inOrder(resultRepository, isolateRepository, alertService);
        order.verify(resultRepository).deleteByUploadId(412L);
        order.verify(isolateRepository).deleteByUploadId(412L);
        order.verify(alertService).reassessAfterRevert(hnch, 412L);
    }

    @Test
    void noSeRevierteUnaCargaQueSigueProcesandose() {
        upload(413L, UploadStatus.PROCESSING);

        assertThatThrownBy(() -> service.revert(413L, "error", ADMIN))
                .isInstanceOf(UploadNotRevertableException.class)
                .hasMessageContaining("procesando");
        verify(resultRepository, never()).deleteByUploadId(anyLong());
    }

    @Test
    void noSeRevierteDosVeces() {
        DataUpload u = upload(414L, UploadStatus.REVERTED);
        u.setRevertedAt(Instant.parse("2026-11-21T22:00:00Z"));

        assertThatThrownBy(() -> service.revert(414L, "otra vez", ADMIN))
                .isInstanceOf(UploadNotRevertableException.class)
                .hasMessageContaining("ya fue revertida");
        verify(isolateRepository, never()).deleteByUploadId(anyLong());
    }

    @Test
    void laCargaInformaLaCompletitudDeLasVariablesObligatoriasDelIns() {
        upload(415L, UploadStatus.COMPLETED);
        java.util.List<Object[]> fila = new java.util.ArrayList<>();
        fila.add(new Object[]{40L, 40L, 38L, 10L});
        when(isolateRepository.completenessOfUpload(415L)).thenReturn(fila);

        var c = service.findById(415L).completeness();

        assertThat(c.isolates()).isEqualTo(40);
        assertThat(c.sexPercentage()).isEqualTo(100.0);
        assertThat(c.agePercentage()).isEqualTo(95.0);
        assertThat(c.wardPercentage()).isEqualTo(25.0);
    }
}
