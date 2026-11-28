package pe.edu.utec.atlasrambackend.access;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.model.Facility;
import pe.edu.utec.atlasrambackend.model.Role;
import pe.edu.utec.atlasrambackend.model.User;
import pe.edu.utec.atlasrambackend.repository.FacilityRepository;
import pe.edu.utec.atlasrambackend.repository.UserRepository;

import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Solicitudes de acceso profesional, sin administración manual en el caso normal:
 *
 * 1. Aprobación automática: si el correo está VERIFICADO (enlace enviado al buzón), su dominio es el dominio
 *    institucional del establecimiento (por ejemplo, essalud.gob.pe) y el rol es coherente con la profesión,
 *    el sistema da el rol solo. Un correo institucional verificado prueba que la persona trabaja ahí.
 * 2. Si no cumple (correo personal, rol poco habitual), la solicitud queda para el ADMINISTRADOR DE ESE
 *    ESTABLECIMIENTO (su jefe), que confirma la colegiatura en el buscador público del colegio. El
 *    administrador global de Atlas RAM ya no es un cuello de botella.
 */
@Service
public class AccessRequestService {

    /** Roles que se pueden pedir. ADMIN nunca se pide: lo asigna otro administrador. */
    static final Set<Role> REQUESTABLE = Set.of(Role.LAB_TECHNICIAN, Role.EPIDEMIOLOGIST, Role.PHARMACIST);

    /** Roles habituales de cada profesión, para avisar si el pedido es poco común. */
    static final Map<ProfessionalBody, Set<Role>> USUAL_ROLES = Map.of(
            ProfessionalBody.CMP, Set.of(Role.EPIDEMIOLOGIST, Role.LAB_TECHNICIAN),
            ProfessionalBody.CTMP, Set.of(Role.LAB_TECHNICIAN),
            ProfessionalBody.CBP, Set.of(Role.LAB_TECHNICIAN),
            ProfessionalBody.CQFP, Set.of(Role.PHARMACIST));

    private final AccessRequestRepository accessRequestRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;

    public AccessRequestService(AccessRequestRepository accessRequestRepository,
                                FacilityRepository facilityRepository,
                                UserRepository userRepository) {
        this.accessRequestRepository = accessRequestRepository;
        this.facilityRepository = facilityRepository;
        this.userRepository = userRepository;
    }

    /** Se llama antes de crear la cuenta: si el pedido no es válido, no se crea nada. */
    @Transactional(readOnly = true)
    public void validate(ProfessionalRequestDTO dto) {
        if (!REQUESTABLE.contains(dto.requestedRole())) {
            throw new BusinessRuleException("Ese rol no se puede solicitar: elige laboratorio, epidemiología o farmacia");
        }
        if (!facilityRepository.existsById(dto.facilityId())) {
            throw new ResourceNotFoundException("Establecimiento", dto.facilityId());
        }
    }

    @Transactional
    public AccessRequestResponseDTO create(String email, ProfessionalRequestDTO dto) {
        AccessRequest saved = createRequest(email, dto);
        tryAutoApprove(saved);
        return toResponse(saved);
    }

    private AccessRequest createRequest(String email, ProfessionalRequestDTO dto) {
        validate(dto);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", email));
        if (accessRequestRepository.existsByUserIdAndStatus(user.getId(), AccessRequestStatus.PENDING)) {
            throw new BusinessRuleException("Ya tienes una solicitud pendiente");
        }
        AccessRequest request = new AccessRequest();
        request.setUser(user);
        request.setFacility(facilityRepository.getReferenceById(dto.facilityId()));
        request.setRequestedRole(dto.requestedRole());
        request.setProfessionalBody(dto.professionalBody());
        request.setRegistrationNumber(dto.registrationNumber());
        return accessRequestRepository.save(request);
    }

    /** ¿Cumple las condiciones de aprobación automática? (correo verificado del dominio + rol habitual) */
    static boolean qualifiesForAutoApproval(AccessRequest r) {
        User user = r.getUser();
        return user.isEmailVerified()
                && r.getFacility().hasEmailDomain(user.emailDomain())
                && USUAL_ROLES.getOrDefault(r.getProfessionalBody(), Set.of()).contains(r.getRequestedRole());
    }

    private void tryAutoApprove(AccessRequest r) {
        if (r.getStatus() == AccessRequestStatus.PENDING && qualifiesForAutoApproval(r)) {
            grant(r);
            r.setStatus(AccessRequestStatus.APPROVED);
            r.setDecidedAt(Instant.now());
            r.setDecidedBy(null);
            r.setNote("Aprobación automática: correo institucional verificado (@" + r.getUser().emailDomain()
                    + ") del establecimiento");
        }
    }

    /** Se llama al verificar el correo: aprueba lo que ya cumple. */
    @Transactional
    public void autoApprovePending(User user) {
        accessRequestRepository.findByUserIdAndStatus(user.getId(), AccessRequestStatus.PENDING)
                .forEach(this::tryAutoApprove);
    }

    /** Pendientes que puede resolver quien consulta: todas si es ADMIN; las de sus establecimientos si es jefe. */
    @Transactional(readOnly = true)
    public List<AccessRequestResponseDTO> pending(String viewerEmail) {
        User viewer = userRepository.findByEmail(viewerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", viewerEmail));
        List<AccessRequest> list;
        if (viewer.getRole() == Role.ADMIN) {
            list = accessRequestRepository.findByStatusOrderByCreatedAtAsc(AccessRequestStatus.PENDING);
        } else if (!viewer.getManagedFacilities().isEmpty()) {
            list = accessRequestRepository.findByStatusAndFacilityIdInOrderByCreatedAtAsc(AccessRequestStatus.PENDING,
                    viewer.getManagedFacilities().stream().map(Facility::getId).toList());
        } else {
            throw new AccessDeniedException("Solo el administrador o el jefe del establecimiento ven las solicitudes");
        }
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AccessRequestResponseDTO> mine(String email) {
        return accessRequestRepository.findByUserEmailOrderByCreatedAtDesc(email).stream()
                .map(this::toResponse).toList();
    }

    /** Da el rol pedido, asigna el establecimiento y cierra las sesiones para que el nuevo rol entre al volver. */
    @Transactional
    public AccessRequestResponseDTO approve(Long id, String deciderEmail) {
        AccessRequest request = pendingRequest(id);
        User decider = requireDecider(request, deciderEmail);
        grant(request);
        decide(request, AccessRequestStatus.APPROVED, decider, null);
        return toResponse(request);
    }

    private static void grant(AccessRequest request) {
        User user = request.getUser();
        user.setRole(request.getRequestedRole());
        user.getFacilities().add(request.getFacility());
        user.revokeSessions();
    }

    /** Puede decidir el ADMIN o un jefe de ese mismo establecimiento (nunca sobre su propia solicitud). */
    private User requireDecider(AccessRequest request, String deciderEmail) {
        User decider = userRepository.findByEmail(deciderEmail)
                .orElseThrow(() -> new AccessDeniedException("Sin permiso para resolver solicitudes"));
        boolean manager = decider.getManagedFacilities().stream()
                .anyMatch(f -> f.getId().equals(request.getFacility().getId()));
        if (decider.getRole() != Role.ADMIN && !manager) {
            throw new AccessDeniedException("Solo el administrador o el jefe de este establecimiento puede decidir");
        }
        if (decider.getId() != null && decider.getId().equals(request.getUser().getId())) {
            throw new AccessDeniedException("Nadie aprueba su propia solicitud");
        }
        return decider;
    }

    @Transactional
    public AccessRequestResponseDTO reject(Long id, String deciderEmail, String note) {
        AccessRequest request = pendingRequest(id);
        decide(request, AccessRequestStatus.REJECTED, requireDecider(request, deciderEmail), note);
        return toResponse(request);
    }

    private AccessRequest pendingRequest(Long id) {
        AccessRequest request = accessRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de acceso", id));
        if (request.getStatus() != AccessRequestStatus.PENDING) {
            throw new BusinessRuleException("La solicitud ya fue resuelta");
        }
        return request;
    }

    private void decide(AccessRequest request, AccessRequestStatus status, User decider, String note) {
        request.setStatus(status);
        request.setDecidedAt(Instant.now());
        request.setDecidedBy(decider);
        request.setNote(note == null || note.isBlank() ? null : note.trim());
    }

    /** Lo que el sistema comprobó por su cuenta, en frases para el administrador. */
    static List<String> checks(AccessRequest r) {
        List<String> checks = new ArrayList<>();
        checks.add("Colegiatura con formato válido (" + r.getProfessionalBody().name() + " "
                + r.getRegistrationNumber() + ")");
        Facility facility = r.getFacility();
        checks.add(facility.getRenipressCode() != null
                ? "Establecimiento con código RENIPRESS " + facility.getRenipressCode()
                : "Establecimiento registrado en Atlas RAM (sin código RENIPRESS cargado)");
        boolean usual = USUAL_ROLES.getOrDefault(r.getProfessionalBody(), Set.of()).contains(r.getRequestedRole());
        checks.add(usual ? "Rol coherente con la profesión" : "Atención: rol poco habitual para esta profesión");
        User user = r.getUser();
        if (!user.isEmailVerified()) {
            checks.add("Correo aún no verificado");
        } else if (facility.hasEmailDomain(user.emailDomain())) {
            checks.add("Correo institucional verificado del establecimiento (@" + user.emailDomain() + ")");
        } else {
            checks.add("Correo verificado, pero no es del dominio del establecimiento (@" + user.emailDomain()
                    + "): confirmar la colegiatura en el buscador del colegio");
        }
        return checks;
    }

    private AccessRequestResponseDTO toResponse(AccessRequest r) {
        Facility f = r.getFacility();
        return new AccessRequestResponseDTO(r.getId(), r.getUser().getEmail(), r.getUser().getFullName(),
                f.getId(), f.getName(), f.getRenipressCode(), r.getRequestedRole(), r.getProfessionalBody(),
                r.getProfessionalBody().label(), r.getRegistrationNumber(), r.getProfessionalBody().lookupUrl(),
                r.getStatus(), r.getCreatedAt(), r.getDecidedAt(), r.getNote(), checks(r));
    }
}
