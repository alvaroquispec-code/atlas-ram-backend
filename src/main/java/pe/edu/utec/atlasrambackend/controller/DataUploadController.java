package pe.edu.utec.atlasrambackend.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utec.atlasrambackend.config.FacilityAccessEvaluator;
import pe.edu.utec.atlasrambackend.dto.DataUploadResponseDTO;
import pe.edu.utec.atlasrambackend.dto.RevertUploadDTO;
import pe.edu.utec.atlasrambackend.dto.UploadRevertResponseDTO;
import pe.edu.utec.atlasrambackend.model.UploadStatus;
import pe.edu.utec.atlasrambackend.service.DataUploadService;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/uploads")
public class DataUploadController {

    private final DataUploadService dataUploadService;
    private final FacilityAccessEvaluator facilityAccess;

    public DataUploadController(DataUploadService dataUploadService, FacilityAccessEvaluator facilityAccess) {
        this.dataUploadService = dataUploadService;
        this.facilityAccess = facilityAccess;
    }

    /** Un técnico solo ve las cargas de sus establecimientos. */
    @GetMapping
    public ResponseEntity<Page<DataUploadResponseDTO>> findAll(
            @PageableDefault(size = 20, sort = "startedAt") Pageable pageable,
            Authentication authentication) {
        Set<Long> allowed = facilityAccess.visibleFacilityIds(authentication).orElse(null);
        return ResponseEntity.ok(dataUploadService.findAll(pageable, allowed));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<DataUploadResponseDTO>> findByStatus(
            @PathVariable UploadStatus status, Authentication authentication) {
        Set<Long> allowed = facilityAccess.visibleFacilityIds(authentication).orElse(null);
        return ResponseEntity.ok(dataUploadService.findByStatus(status, allowed));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@facilityAccess.canAccessUpload(authentication, #id)")
    public ResponseEntity<DataUploadResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(dataUploadService.findById(id));
    }

    @GetMapping("/{id}/isolates/count")
    @PreAuthorize("@facilityAccess.canAccessUpload(authentication, #id)")
    public ResponseEntity<Map<String, Long>> countIsolates(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("isolates", dataUploadService.countIsolates(id)));
    }

    /** Deshace una carga equivocada. Solo ADMIN (regla también en SecurityConfig) y con motivo obligatorio. */
    @PostMapping("/{id}/revert")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UploadRevertResponseDTO> revert(@PathVariable Long id,
                                                          @Valid @RequestBody RevertUploadDTO dto,
                                                          Authentication authentication) {
        return ResponseEntity.ok(dataUploadService.revert(id, dto.reason(), authentication.getName()));
    }
}
