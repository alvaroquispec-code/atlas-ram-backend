package pe.edu.utec.atlasrambackend.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import pe.edu.utec.atlasrambackend.dto.CreateIsolateDTO;
import pe.edu.utec.atlasrambackend.dto.IsolateResponseDTO;
import pe.edu.utec.atlasrambackend.dto.SusceptibilityResultResponseDTO;
import pe.edu.utec.atlasrambackend.service.IsolateService;
import pe.edu.utec.atlasrambackend.service.SusceptibilityResultService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/isolates")
public class IsolateController {

    private final IsolateService isolateService;
    private final SusceptibilityResultService resultService;

    public IsolateController(IsolateService isolateService,
                             SusceptibilityResultService resultService) {
        this.isolateService = isolateService;
        this.resultService = resultService;
    }

    @GetMapping
    public ResponseEntity<Page<IsolateResponseDTO>> findAll(
            @RequestParam(required = false) Long facilityId,
            @PageableDefault(size = 20, sort = "collectionDate") Pageable pageable) {
        return ResponseEntity.ok(facilityId == null
                ? isolateService.findAll(pageable)
                : isolateService.findByFacility(facilityId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IsolateResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(isolateService.findById(id));
    }

    @GetMapping("/{id}/results")
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        isolateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
