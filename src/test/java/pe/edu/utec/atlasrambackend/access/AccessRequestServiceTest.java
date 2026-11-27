package pe.edu.utec.atlasrambackend.access;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.model.Facility;
import pe.edu.utec.atlasrambackend.model.Role;
import pe.edu.utec.atlasrambackend.model.User;
import pe.edu.utec.atlasrambackend.repository.FacilityRepository;
import pe.edu.utec.atlasrambackend.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessRequestServiceTest {

    private final AccessRequestRepository requests = mock(AccessRequestRepository.class);
    private final FacilityRepository facilities = mock(FacilityRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AccessRequestService service = new AccessRequestService(requests, facilities, users);

    private final Facility hnch = new Facility();
    private final User maria = new User();

    @BeforeEach
    void setUp() {
        hnch.setId(1L);
        hnch.setName("Hospital Nacional Cayetano Heredia");
        maria.setId(7L);
        maria.setEmail("maria@hnch.test");
        maria.setFullName("María Guamán");
        maria.setRole(Role.PUBLIC_VIEWER);
        when(facilities.existsById(1L)).thenReturn(true);
        when(facilities.getReferenceById(1L)).thenReturn(hnch);
        when(users.findByEmail("maria@hnch.test")).thenReturn(Optional.of(maria));
        when(requests.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static ProfessionalRequestDTO tecnologa(Role role) {
        return new ProfessionalRequestDTO(1L, role, ProfessionalBody.CTMP, "12345");
    }

    @Test
    void creaLaSolicitudConLoQueElSistemaYaComprobo() {
        AccessRequestResponseDTO r = service.create("maria@hnch.test", tecnologa(Role.LAB_TECHNICIAN));
        assertThat(r.status()).isEqualTo(AccessRequestStatus.PENDING);
        assertThat(r.checks()).contains("Rol coherente con la profesión");
        assertThat(maria.getRole()).isEqualTo(Role.PUBLIC_VIEWER);
    }

    @Test
    void nadiePuedePedirElRolDeAdministrador() {
        assertThatThrownBy(() -> service.validate(tecnologa(Role.ADMIN))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void avisaSiElRolEsPocoHabitualParaLaProfesion() {
        AccessRequestResponseDTO r = service.create("maria@hnch.test", tecnologa(Role.PHARMACIST));
        assertThat(r.checks()).anyMatch(c -> c.startsWith("Atención"));
    }

    @Test
    void alAprobarDaElRolAsignaElEstablecimientoYCierraLasSesiones() {
        AccessRequest request = new AccessRequest();
        request.setId(3L);
        request.setUser(maria);
        request.setFacility(hnch);
        request.setRequestedRole(Role.LAB_TECHNICIAN);
        request.setProfessionalBody(ProfessionalBody.CTMP);
        request.setRegistrationNumber("12345");
        when(requests.findById(3L)).thenReturn(Optional.of(request));
        int version = maria.getTokenVersion();

        AccessRequestResponseDTO r = service.approve(3L, "admin@atlasram.test");

        assertThat(r.status()).isEqualTo(AccessRequestStatus.APPROVED);
        assertThat(maria.getRole()).isEqualTo(Role.LAB_TECHNICIAN);
        assertThat(maria.getFacilities()).contains(hnch);
        assertThat(maria.getTokenVersion()).isEqualTo(version + 1);
        assertThatThrownBy(() -> service.approve(3L, "admin@atlasram.test")).isInstanceOf(BusinessRuleException.class);
    }
}
