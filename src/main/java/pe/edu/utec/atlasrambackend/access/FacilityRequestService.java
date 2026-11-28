package pe.edu.utec.atlasrambackend.access;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.model.Facility;
import pe.edu.utec.atlasrambackend.model.FacilitySector;
import pe.edu.utec.atlasrambackend.model.User;
import pe.edu.utec.atlasrambackend.repository.DistrictRepository;
import pe.edu.utec.atlasrambackend.repository.FacilityRepository;
import pe.edu.utec.atlasrambackend.repository.UserRepository;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Registro de establecimientos (clínicas y hospitales) con RUC y código IPRESS.
 *
 * Regla central: el sector (público o privado) lo decide el padrón RENIPRESS de SUSALUD, nunca lo que la
 * clínica declara. Una clínica privada que dice ser pública queda registrada como PRIVADA (con licencia).
 *
 * - Público: RENIPRESS dice público, está activo y quien pide tiene correo VERIFICADO de un dominio del
 *   Estado (.gob.pe). Se aprueba solo, por convenio y sin costo.
 * - Privado: queda pendiente porque implica una licencia (contrato con su RUC); el sistema ya hizo todas
 *   las comprobaciones y el administrador aprueba con un clic.
 * Al aprobarse, quien pidió queda como jefe del establecimiento: aprueba a su propio personal.
 */
@Service
public class FacilityRequestService {

    private static final int[] RUC_WEIGHTS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private final FacilityRequestRepository requests;
    private final RenipressRepository renipress;
    private final FacilityRepository facilities;
    private final DistrictRepository districts;
    private final UserRepository users;

    public FacilityRequestService(FacilityRequestRepository requests, RenipressRepository renipress,
                                  FacilityRepository facilities, DistrictRepository districts,
                                  UserRepository users) {
        this.requests = requests;
        this.renipress = renipress;
        this.facilities = facilities;
        this.districts = districts;
        this.users = users;
    }

    /** Dígito verificador del RUC (módulo 11 de SUNAT). */
    public static boolean isValidRuc(String ruc) {
        if (ruc == null || !ruc.matches("^(10|15|16|17|20)[0-9]{9}$")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += (ruc.charAt(i) - '0') * RUC_WEIGHTS[i];
        }
        int check = 11 - (sum % 11);
        if (check == 10) {
            check = 0;
        } else if (check == 11) {
            check = 1;
        }
        return check == ruc.charAt(10) - '0';
    }

    /** Dominios del Estado peruano: un privado no puede tener un buzón en ellos. */
    public static boolean isStateDomain(String domain) {
        return domain != null && (domain.equals("gob.pe") || domain.endsWith(".gob.pe"));
    }

    @Transactional
    public FacilityRequestResponseDTO create(String email, FacilityRequestDTO dto) {
        User requester = users.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Usuario", email));
        if (!isValidRuc(dto.ruc())) {
            throw new BusinessRuleException("El RUC no es válido (no coincide su dígito verificador)");
        }
        if (facilities.findByRenipressCode(dto.renipressCode()).isPresent()) {
            throw new BusinessRuleException("Ese código IPRESS ya está registrado en Atlas RAM");
        }
        if (requests.existsByRenipressCodeAndStatus(dto.renipressCode(), AccessRequestStatus.PENDING)) {
            throw new BusinessRuleException("Ya hay una solicitud pendiente para ese código IPRESS");
        }
        FacilityRequest r = new FacilityRequest();
        r.setRequester(requester);
        r.setName(dto.name().trim());
        r.setRuc(dto.ruc());
        r.setRenipressCode(dto.renipressCode());
        r.setDistrict(districts.findById(dto.districtId())
                .orElseThrow(() -> new ResourceNotFoundException("Distrito", dto.districtId())));
        r.setClaimedSector(dto.claimedSector());
        requests.save(r);
        Optional<Renipress> entry = renipress.findById(r.getRenipressCode());
        if (qualifiesForAutoApproval(r, entry)) {
            approveInternal(r, entry, null, "Aprobación automática: establecimiento público según RENIPRESS y "
                    + "correo verificado del Estado (@" + requester.emailDomain() + ")");
        }
        return toResponse(r, entry);
    }

    static boolean qualifiesForAutoApproval(FacilityRequest r, Optional<Renipress> entry) {
        User requester = r.getRequester();
        return entry.isPresent() && entry.get().isPublicSector() && isActive(entry.get())
                && requester.isEmailVerified() && isStateDomain(requester.emailDomain())
                && namesMatch(r.getName(), entry.get().getName());
    }

    @Transactional(readOnly = true)
    public List<FacilityRequestResponseDTO> pending() {
        return requests.findByStatusOrderByCreatedAtAsc(AccessRequestStatus.PENDING).stream()
                .map(r -> toResponse(r, renipress.findById(r.getRenipressCode()))).toList();
    }

    @Transactional(readOnly = true)
    public List<FacilityRequestResponseDTO> mine(String email) {
        return requests.findByRequesterEmailOrderByCreatedAtDesc(email).stream()
                .map(r -> toResponse(r, renipress.findById(r.getRenipressCode()))).toList();
    }

    @Transactional
    public FacilityRequestResponseDTO approve(Long id, String adminEmail) {
        FacilityRequest r = pendingRequest(id);
        Optional<Renipress> entry = renipress.findById(r.getRenipressCode());
        if (!r.getRequester().isEmailVerified()) {
            throw new BusinessRuleException("Quien pide aún no confirma su correo");
        }
        approveInternal(r, entry, users.findByEmail(adminEmail).orElse(null), null);
        return toResponse(r, entry);
    }

    @Transactional
    public FacilityRequestResponseDTO reject(Long id, String adminEmail, String note) {
        FacilityRequest r = pendingRequest(id);
        r.setStatus(AccessRequestStatus.REJECTED);
        r.setDecidedAt(Instant.now());
        r.setDecidedBy(users.findByEmail(adminEmail).orElse(null));
        r.setNote(note == null || note.isBlank() ? null : note.trim());
        return toResponse(r, renipress.findById(r.getRenipressCode()));
    }

    /** Crea el establecimiento con el sector de RENIPRESS y deja a quien pidió como su jefe. */
    private void approveInternal(FacilityRequest r, Optional<Renipress> entry, User decider, String note) {
        if (facilities.findByRenipressCode(r.getRenipressCode()).isPresent()) {
            throw new BusinessRuleException("Ese código IPRESS ya está registrado en Atlas RAM");
        }
        Facility f = new Facility();
        f.setCode("IPRESS-" + r.getRenipressCode());
        String officialName = entry.map(Renipress::getName).orElse(r.getName());
        f.setName(officialName.length() > 200 ? officialName.substring(0, 200) : officialName);
        f.setDistrict(r.getDistrict());
        f.setRenipressCode(r.getRenipressCode());
        f.setRuc(r.getRuc());
        f.setSector(sectorOf(entry));
        User requester = r.getRequester();
        if (requester.isEmailVerified() && !isPublicMailbox(requester.emailDomain())) {
            f.setEmailDomains(requester.emailDomain());
        }
        facilities.save(f);
        requester.getManagedFacilities().add(f);
        requester.getFacilities().add(f);
        r.setFacility(f);
        r.setStatus(AccessRequestStatus.APPROVED);
        r.setDecidedAt(Instant.now());
        r.setDecidedBy(decider);
        r.setNote(note);
    }

    /** Sin RENIPRESS no se reconoce como público: por defecto, privado. */
    static FacilitySector sectorOf(Optional<Renipress> entry) {
        return entry.isPresent() && entry.get().isPublicSector() ? FacilitySector.PUBLIC : FacilitySector.PRIVATE;
    }

    private static final Set<String> PUBLIC_MAILBOXES = Set.of("gmail.com", "hotmail.com", "outlook.com",
            "yahoo.com", "yahoo.es", "live.com", "icloud.com", "protonmail.com");

    /** Correos gratuitos: no sirven como dominio institucional (cualquiera puede crear uno). */
    static boolean isPublicMailbox(String domain) {
        return domain == null || PUBLIC_MAILBOXES.contains(domain);
    }

    static boolean isActive(Renipress entry) {
        return entry.getStatus() == null || entry.getStatus().toUpperCase(Locale.ROOT).contains("ACTIV");
    }

    /** Coinciden si comparten la mayoría de palabras significativas (sin tildes, mayúsculas ni conectores). */
    static boolean namesMatch(String declared, String official) {
        Set<String> a = words(declared);
        Set<String> b = words(official);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        Set<String> common = new HashSet<>(a);
        common.retainAll(b);
        return common.size() * 2 >= Math.min(a.size(), b.size());
    }

    private static final Set<String> STOP = Set.of("DE", "DEL", "LA", "EL", "LOS", "LAS", "Y", "SAC", "SA",
            "S", "A", "C", "EIRL", "SRL", "HOSPITAL", "CLINICA", "NACIONAL");

    private static Set<String> words(String name) {
        if (name == null) {
            return Set.of();
        }
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 ]", " ");
        Set<String> out = new HashSet<>(Arrays.asList(plain.trim().split("\\s+")));
        out.removeIf(w -> w.isBlank() || STOP.contains(w));
        return out;
    }

    private FacilityRequest pendingRequest(Long id) {
        FacilityRequest r = requests.findById(id).orElseThrow(() -> new ResourceNotFoundException("Solicitud", id));
        if (r.getStatus() != AccessRequestStatus.PENDING) {
            throw new BusinessRuleException("La solicitud ya fue resuelta");
        }
        return r;
    }

    static List<String> checks(FacilityRequest r, Optional<Renipress> entry) {
        List<String> checks = new ArrayList<>();
        checks.add(isValidRuc(r.getRuc()) ? "RUC válido (dígito verificador de SUNAT)" : "RUC inválido");
        if (entry.isEmpty()) {
            checks.add("El código IPRESS " + r.getRenipressCode() + " no figura en el padrón RENIPRESS cargado: "
                    + "se registraría como PRIVADO");
        } else {
            Renipress e = entry.get();
            checks.add("RENIPRESS: " + e.getName() + " · " + e.getInstitution()
                    + (e.getCategory() == null ? "" : " · categoría " + e.getCategory()));
            checks.add(isActive(e) ? "Establecimiento activo en RENIPRESS" : "Atención: RENIPRESS no lo marca activo");
            checks.add(namesMatch(r.getName(), e.getName()) ? "El nombre coincide con RENIPRESS"
                    : "Atención: el nombre declarado no coincide con RENIPRESS");
            FacilitySector official = sectorOf(entry);
            if (r.getClaimedSector() == FacilitySector.PUBLIC && official == FacilitySector.PRIVATE) {
                checks.add("Declaró ser PÚBLICO pero RENIPRESS lo registra como PRIVADO: se registra como privado "
                        + "(con licencia)");
            } else {
                checks.add("Sector según RENIPRESS: " + (official == FacilitySector.PUBLIC ? "público" : "privado"));
            }
        }
        User requester = r.getRequester();
        if (!requester.isEmailVerified()) {
            checks.add("Quien pide aún no confirma su correo");
        } else if (isStateDomain(requester.emailDomain())) {
            checks.add("Correo verificado del Estado (@" + requester.emailDomain() + ")");
        } else if (isPublicMailbox(requester.emailDomain())) {
            checks.add("Atención: correo gratuito (@" + requester.emailDomain() + "), no institucional");
        } else {
            checks.add("Correo verificado de la organización (@" + requester.emailDomain() + ")");
        }
        return checks;
    }

    private FacilityRequestResponseDTO toResponse(FacilityRequest r, Optional<Renipress> entry) {
        return new FacilityRequestResponseDTO(r.getId(), r.getRequester().getEmail(), r.getName(), r.getRuc(),
                r.getRenipressCode(), r.getDistrict().getName(), r.getClaimedSector(),
                entry.isPresent() ? sectorOf(entry) : null,
                entry.map(Renipress::getName).orElse(null), entry.map(Renipress::getInstitution).orElse(null),
                r.getStatus(), r.getCreatedAt(), r.getNote(),
                r.getFacility() == null ? null : r.getFacility().getId(), checks(r, entry));
    }
}
