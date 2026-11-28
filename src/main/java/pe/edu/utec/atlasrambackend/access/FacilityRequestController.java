package pe.edu.utec.atlasrambackend.access;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Registro de establecimientos. /mine para cualquier usuario con sesión; el resto, ADMIN (SecurityConfig). */
@RestController
@RequestMapping("/facility-requests")
public class FacilityRequestController {

    private final FacilityRequestService service;

    public FacilityRequestController(FacilityRequestService service) {
        this.service = service;
    }

    @PostMapping("/mine")
    public ResponseEntity<FacilityRequestResponseDTO> create(@Valid @RequestBody FacilityRequestDTO dto,
                                                             Authentication authentication) {
        return ResponseEntity.status(201).body(service.create(authentication.getName(), dto));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<FacilityRequestResponseDTO>> mine(Authentication authentication) {
        return ResponseEntity.ok(service.mine(authentication.getName()));
    }

    @GetMapping
    public ResponseEntity<List<FacilityRequestResponseDTO>> pending() {
        return ResponseEntity.ok(service.pending());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<FacilityRequestResponseDTO> approve(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(service.approve(id, authentication.getName()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<FacilityRequestResponseDTO> reject(@PathVariable Long id,
                                                             @Valid @RequestBody(required = false)
                                                             AccessRequestController.RejectDTO dto,
                                                             Authentication authentication) {
        return ResponseEntity.ok(service.reject(id, authentication.getName(), dto == null ? null : dto.note()));
    }
}
