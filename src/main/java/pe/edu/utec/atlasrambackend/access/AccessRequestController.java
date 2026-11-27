package pe.edu.utec.atlasrambackend.access;

import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Solicitudes de acceso profesional. /mine para cualquier usuario; el resto, solo ADMIN (SecurityConfig). */
@RestController
@RequestMapping("/access-requests")
public class AccessRequestController {

    private final AccessRequestService service;

    public AccessRequestController(AccessRequestService service) {
        this.service = service;
    }

    public record RejectDTO(@Size(max = 300) String note) {
    }

    @GetMapping("/mine")
    public ResponseEntity<List<AccessRequestResponseDTO>> mine(Authentication authentication) {
        return ResponseEntity.ok(service.mine(authentication.getName()));
    }

    /** Un usuario ya registrado (por ejemplo, desde la app) también puede pedir acceso profesional. */
    @PostMapping("/mine")
    public ResponseEntity<AccessRequestResponseDTO> request(@jakarta.validation.Valid @RequestBody ProfessionalRequestDTO dto,
                                                            Authentication authentication) {
        return ResponseEntity.status(201).body(service.create(authentication.getName(), dto));
    }

    @GetMapping
    public ResponseEntity<List<AccessRequestResponseDTO>> pending() {
        return ResponseEntity.ok(service.pending());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<AccessRequestResponseDTO> approve(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(service.approve(id, authentication.getName()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<AccessRequestResponseDTO> reject(@PathVariable Long id,
                                                           @jakarta.validation.Valid @RequestBody(required = false) RejectDTO dto,
                                                           Authentication authentication) {
        return ResponseEntity.ok(service.reject(id, authentication.getName(), dto == null ? null : dto.note()));
    }
}
