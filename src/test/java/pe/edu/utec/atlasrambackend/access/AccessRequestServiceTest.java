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
    private final User admin = new User();

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
        admin.setId(1L);
        admin.setEmail("admin@atlasram.test");
        admin.setRole(Role.ADMIN);
        when(users.findByEmail("admin@atlasram.test")).thenReturn(Optional.of(admin));
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

    @Test
    void conCorreoInstitucionalVerificadoSeApruebaSolaSinIntervencionManual() {
        hnch.setEmailDomains("hospitalcayetano.gob.pe");
        maria.setEmail("maria@hospitalcayetano.gob.pe");
        maria.setEmailVerifiedAt(java.time.Instant.now());
        when(users.findByEmail("maria@hospitalcayetano.gob.pe")).thenReturn(Optional.of(maria));

        AccessRequestResponseDTO r = service.create("maria@hospitalcayetano.gob.pe", tecnologa(Role.LAB_TECHNICIAN));

        assertThat(r.status()).isEqualTo(AccessRequestStatus.APPROVED);
        assertThat(r.note()).startsWith("Aprobación automática");
        assertThat(maria.getRole()).isEqualTo(Role.LAB_TECHNICIAN);
    }

    @Test
    void sinVerificarElCorreoNoHayAprobacionAutomatica() {
        hnch.setEmailDomains("hospitalcayetano.gob.pe");
        maria.setEmail("maria@hospitalcayetano.gob.pe");
        when(users.findByEmail("maria@hospitalcayetano.gob.pe")).thenReturn(Optional.of(maria));

        AccessRequestResponseDTO r = service.create("maria@hospitalcayetano.gob.pe", tecnologa(Role.LAB_TECHNICIAN));

        assertThat(r.status()).isEqualTo(AccessRequestStatus.PENDING);
        assertThat(r.checks()).contains("Correo aún no verificado");
    }

    @Test
    void conCorreoPersonalQuedaParaElJefeDelEstablecimiento() {
        hnch.setEmailDomains("hospitalcayetano.gob.pe");
        maria.setEmailVerifiedAt(java.time.Instant.now());
        AccessRequestResponseDTO r = service.create("maria@hnch.test", tecnologa(Role.LAB_TECHNICIAN));
        assertThat(r.status()).isEqualTo(AccessRequestStatus.PENDING);
    }

    @Test
    void elJefeDeOtroEstablecimientoNoPuedeAprobar() {
        AccessRequest request = new AccessRequest();
        request.setId(4L);
        request.setUser(maria);
        request.setFacility(hnch);
        request.setRequestedRole(Role.LAB_TECHNICIAN);
        request.setProfessionalBody(ProfessionalBody.CTMP);
        request.setRegistrationNumber("12345");
        when(requests.findById(4L)).thenReturn(Optional.of(request));
        Facility otro = new Facility();
        otro.setId(2L);
        User jefeOtro = new User();
        jefeOtro.setId(9L);
        jefeOtro.setEmail("jefe@otro.test");
        jefeOtro.setRole(Role.LAB_TECHNICIAN);
        jefeOtro.getManagedFacilities().add(otro);
        when(users.findByEmail("jefe@otro.test")).thenReturn(Optional.of(jefeOtro));

        assertThatThrownBy(() -> service.approve(4L, "jefe@otro.test"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        User jefe = new User();
        jefe.setId(10L);
        jefe.setEmail("jefe@hnch.test");
        jefe.setRole(Role.LAB_TECHNICIAN);
        jefe.getManagedFacilities().add(hnch);
        when(users.findByEmail("jefe@hnch.test")).thenReturn(Optional.of(jefe));
        assertThat(service.approve(4L, "jefe@hnch.test").status()).isEqualTo(AccessRequestStatus.APPROVED);
    }
}
