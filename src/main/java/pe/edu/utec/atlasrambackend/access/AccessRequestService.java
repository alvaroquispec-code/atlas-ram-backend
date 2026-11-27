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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Solicitudes de acceso profesional. Sin intervención manual, el sistema comprueba lo que se puede comprobar
 * (formato de la colegiatura, que el establecimiento exista y tenga código RENIPRESS, que el rol sea clínico y
 * coherente con la profesión). La colegiatura se confirma en el buscador público del colegio, que no tiene
 * API abierta: el administrador abre el enlace y aprueba con un clic.
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
        return toResponse(accessRequestRepository.save(request));
    }

    @Transactional(readOnly = true)
    public List<AccessRequestResponseDTO> pending() {
        return accessRequestRepository.findByStatusOrderByCreatedAtAsc(AccessRequestStatus.PENDING).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AccessRequestResponseDTO> mine(String email) {
        return accessRequestRepository.findByUserEmailOrderByCreatedAtDesc(email).stream()
                .map(this::toResponse).toList();
    }

    /** Da el rol pedido, asigna el establecimiento y cierra las sesiones para que el nuevo rol entre al volver. */
    @Transactional
    public AccessRequestResponseDTO approve(Long id, String adminEmail) {
        AccessRequest request = pendingRequest(id);
        User user = request.getUser();
        user.setRole(request.getRequestedRole());
        user.getFacilities().add(request.getFacility());
        user.revokeSessions();
        decide(request, AccessRequestStatus.APPROVED, adminEmail, null);
        return toResponse(request);
    }

    @Transactional
    public AccessRequestResponseDTO reject(Long id, String adminEmail, String note) {
        AccessRequest request = pendingRequest(id);
        decide(request, AccessRequestStatus.REJECTED, adminEmail, note);
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

    private void decide(AccessRequest request, AccessRequestStatus status, String adminEmail, String note) {
        request.setStatus(status);
        request.setDecidedAt(Instant.now());
        request.setDecidedBy(userRepository.findByEmail(adminEmail).orElse(null));
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
