package pe.edu.utec.atlasrambackend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import pe.edu.utec.atlasrambackend.repository.DataUploadRepository;
import pe.edu.utec.atlasrambackend.repository.IsolateRepository;
import pe.edu.utec.atlasrambackend.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FacilityAccessEvaluatorTest {

    private static final String LAB_EMAIL = "lab.hnch@atlasram.test";

    private UserRepository userRepository;
    private IsolateRepository isolateRepository;
    private DataUploadRepository uploadRepository;
    private FacilityAccessEvaluator evaluator;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        isolateRepository = mock(IsolateRepository.class);
        uploadRepository = mock(DataUploadRepository.class);
        evaluator = new FacilityAccessEvaluator(userRepository, isolateRepository, uploadRepository);
    }

    private static Authentication as(String email, String role) {
        return new UsernamePasswordAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority(role)));
    }

    @Test
    void tecnicoPuedeAgregarResultadosEnSuEstablecimiento() {
        when(isolateRepository.findFacilityIdById(10L)).thenReturn(Optional.of(1L));
        when(userRepository.isAssignedToFacility(LAB_EMAIL, 1L)).thenReturn(true);

        assertThat(evaluator.canAccessIsolate(as(LAB_EMAIL, "ROLE_LAB_TECHNICIAN"), 10L)).isTrue();
    }

    @Test
    void tecnicoNoPuedeAgregarResultadosEnOtroEstablecimiento() {
        when(isolateRepository.findFacilityIdById(20L)).thenReturn(Optional.of(2L));
        when(userRepository.isAssignedToFacility(LAB_EMAIL, 2L)).thenReturn(false);

        assertThat(evaluator.canAccessIsolate(as(LAB_EMAIL, "ROLE_LAB_TECHNICIAN"), 20L)).isFalse();
    }

    @Test
    void adminPuedeEnCualquierEstablecimiento() {
        assertThat(evaluator.canAccessIsolate(as("admin@atlasram.test", "ROLE_ADMIN"), 20L)).isTrue();
        verifyNoInteractions(isolateRepository, userRepository);
    }

    @Test
    void aislamientoInexistenteSeDejaPasarParaQueElServicioResponda404() {
        when(isolateRepository.findFacilityIdById(999L)).thenReturn(Optional.empty());

        assertThat(evaluator.canAccessIsolate(as(LAB_EMAIL, "ROLE_LAB_TECHNICIAN"), 999L)).isTrue();
    }

    @Test
    void sinAutenticacionNoHayAcceso() {
        assertThat(evaluator.canAccessIsolate(null, 10L)).isFalse();
    }

    @Test
    void tecnicoSoloVeLosEstablecimientosAsignados() {
        when(userRepository.findFacilityIdsByEmail(LAB_EMAIL)).thenReturn(Set.of(1L, 3L));

        assertThat(evaluator.visibleFacilityIds(as(LAB_EMAIL, "ROLE_LAB_TECHNICIAN")))
                .contains(Set.of(1L, 3L));
    }

    @Test
    void epidemiologoVeTodaLaRedSinRestriccion() {
        assertThat(evaluator.visibleFacilityIds(as("epi@atlasram.test", "ROLE_EPIDEMIOLOGIST"))).isEmpty();
        verifyNoInteractions(userRepository);
    }

    @Test
    void tecnicoSinEstablecimientosNoVeNada() {
        when(userRepository.findFacilityIdsByEmail(LAB_EMAIL)).thenReturn(Set.of());

        assertThat(evaluator.visibleFacilityIds(as(LAB_EMAIL, "ROLE_LAB_TECHNICIAN")))
                .contains(Set.of());
    }

    @Test
    void tecnicoVeLasCargasDeSuEstablecimiento() {
        when(uploadRepository.findFacilityIdById(412L)).thenReturn(Optional.of(1L));
        when(userRepository.isAssignedToFacility(LAB_EMAIL, 1L)).thenReturn(true);

        assertThat(evaluator.canAccessUpload(as(LAB_EMAIL, "ROLE_LAB_TECHNICIAN"), 412L)).isTrue();
    }

    @Test
    void tecnicoNoVeLasCargasDeOtroEstablecimiento() {
        // Sin este control, cambiando el número en /uploads/{id} se leía el registro de errores de otro hospital.
        when(uploadRepository.findFacilityIdById(500L)).thenReturn(Optional.of(6L));
        when(userRepository.isAssignedToFacility(LAB_EMAIL, 6L)).thenReturn(false);

        assertThat(evaluator.canAccessUpload(as(LAB_EMAIL, "ROLE_LAB_TECHNICIAN"), 500L)).isFalse();
    }
}
