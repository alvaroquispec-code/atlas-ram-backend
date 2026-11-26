package pe.edu.utec.atlasrambackend.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import pe.edu.utec.atlasrambackend.config.FacilityAccessEvaluator;
import pe.edu.utec.atlasrambackend.dto.AddResultsDTO;
import pe.edu.utec.atlasrambackend.dto.CreateIsolateDTO;
import pe.edu.utec.atlasrambackend.dto.InterpretationPreviewDTO;
import pe.edu.utec.atlasrambackend.dto.IsolateResponseDTO;
import pe.edu.utec.atlasrambackend.model.Interpretation;
import pe.edu.utec.atlasrambackend.dto.SusceptibilityResultResponseDTO;
import pe.edu.utec.atlasrambackend.model.Specimen;
import pe.edu.utec.atlasrambackend.service.IsolateService;
import pe.edu.utec.atlasrambackend.service.SusceptibilityResultService;

import java.net.URI;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/isolates")
public class IsolateController {

    private final IsolateService isolateService;
    private final SusceptibilityResultService resultService;
    private final FacilityAccessEvaluator facilityAccess;

    public IsolateController(IsolateService isolateService,
                             SusceptibilityResultService resultService,
                             FacilityAccessEvaluator facilityAccess) {
        this.isolateService = isolateService;
        this.resultService = resultService;
        this.facilityAccess = facilityAccess;
    }

    /**
     * Un técnico de laboratorio solo lista aislamientos de sus establecimientos: si pide otro
     * establecimiento recibe 403, y sin filtro el listado se restringe a los suyos (OWASP API1, BOLA).
     */
    @GetMapping
    @PreAuthorize("#facilityId == null or @facilityAccess.canAccess(authentication, #facilityId)")
    public ResponseEntity<Page<IsolateResponseDTO>> findAll(
            @RequestParam(required = false) Long facilityId,
            @RequestParam(required = false) Long microorganismId,
            @RequestParam(required = false) Specimen specimen,
            @PageableDefault(size = 20, sort = "collectionDate") Pageable pageable,
            Authentication authentication) {
        Set<Long> allowedFacilityIds = facilityAccess.visibleFacilityIds(authentication).orElse(null);
        return ResponseEntity.ok(
                isolateService.search(facilityId, microorganismId, specimen, allowedFacilityIds, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@facilityAccess.canAccessIsolate(authentication, #id)")
    public ResponseEntity<IsolateResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(isolateService.findById(id));
    }

    @GetMapping("/{id}/results")
    @PreAuthorize("@facilityAccess.canAccessIsolate(authentication, #id)")
    public ResponseEntity<List<SusceptibilityResultResponseDTO>> findResults(@PathVariable Long id) {
        return ResponseEntity.ok(resultService.findByIsolate(id));
    }

    /** Un técnico de laboratorio solo registra aislamientos de sus establecimientos. */
    @PostMapping
    @PreAuthorize("@facilityAccess.canAccess(authentication, #dto.facilityId())")
    public ResponseEntity<IsolateResponseDTO> create(@Valid @RequestBody CreateIsolateDTO dto,
                                                     UriComponentsBuilder uriBuilder) {
        IsolateResponseDTO created = isolateService.create(dto);
        URI location = uriBuilder.path("/isolates/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** Cómo se interpretaría un resultado (S, I o R) antes de guardarlo; no guarda nada. */
    @GetMapping("/interpretation-preview")
    public ResponseEntity<InterpretationPreviewDTO> previewInterpretation(
            @RequestParam Long microorganismId,
            @RequestParam Long antibioticId,
            @RequestParam(required = false) Specimen specimen,
            @RequestParam(required = false) String mic,
            @RequestParam(required = false) Integer diskMm,
            @RequestParam(required = false) Interpretation reported) {
        return ResponseEntity.ok(isolateService.previewInterpretation(
                microorganismId, antibioticId, specimen, mic, diskMm, reported));
    }

    /** Antibióticos que llegan después: se agregan al aislamiento, todos o ninguno. */
    @PostMapping("/{id}/results")
    @PreAuthorize("@facilityAccess.canAccessIsolate(authentication, #id)")
    public ResponseEntity<IsolateResponseDTO> addResults(@PathVariable Long id, @Valid @RequestBody AddResultsDTO dto) {
        return ResponseEntity.ok(isolateService.addResults(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        isolateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
