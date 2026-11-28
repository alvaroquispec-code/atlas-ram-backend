package pe.edu.utec.atlasrambackend.access;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.model.Facility;
import pe.edu.utec.atlasrambackend.model.User;
import pe.edu.utec.atlasrambackend.repository.FacilityRepository;
import pe.edu.utec.atlasrambackend.repository.UserRepository;

import java.util.List;

/**
 * Jefes de establecimiento (solo ADMIN, SecurityConfig): el administrador de Atlas RAM nombra al jefe de
 * cada hospital una sola vez; desde ahí, el jefe aprueba a su propio personal.
 */
@RestController
@RequestMapping("/facility-managers")
public class FacilityManagerController {

    private final UserRepository users;
    private final FacilityRepository facilities;

    public FacilityManagerController(UserRepository users, FacilityRepository facilities) {
        this.users = users;
        this.facilities = facilities;
    }

    public record ManagerDTO(@NotNull Long facilityId, @NotBlank @Email String email) {
    }

    public record ManagerResponseDTO(Long facilityId, String facilityName, Long userId, String email,
                                     String fullName) {
    }

    @GetMapping("/{facilityId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ManagerResponseDTO>> list(@PathVariable Long facilityId) {
        Facility f = facilities.findById(facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Establecimiento", facilityId));
        return ResponseEntity.ok(users.findManagersOf(facilityId).stream()
                .map(u -> new ManagerResponseDTO(f.getId(), f.getName(), u.getId(), u.getEmail(), u.getFullName()))
                .toList());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ManagerResponseDTO> add(@Valid @RequestBody ManagerDTO dto) {
        Facility f = facilities.findById(dto.facilityId())
                .orElseThrow(() -> new ResourceNotFoundException("Establecimiento", dto.facilityId()));
        User u = users.findByEmail(dto.email().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", dto.email()));
        u.getManagedFacilities().add(f);
        u.getFacilities().add(f);
        return ResponseEntity.ok(new ManagerResponseDTO(f.getId(), f.getName(), u.getId(), u.getEmail(),
                u.getFullName()));
    }

    @DeleteMapping("/{facilityId}/{userId}")
    @Transactional
    public ResponseEntity<Void> remove(@PathVariable Long facilityId, @PathVariable Long userId) {
        User u = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Usuario", userId));
        u.getManagedFacilities().removeIf(f -> f.getId().equals(facilityId));
        return ResponseEntity.noContent().build();
    }
}
