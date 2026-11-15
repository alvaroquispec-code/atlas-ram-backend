
package pe.edu.utec.atlasrambackend.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import pe.edu.utec.atlasrambackend.dto.CreateSusceptibilityResultDTO;
import pe.edu.utec.atlasrambackend.dto.SusceptibilityResultResponseDTO;
import pe.edu.utec.atlasrambackend.service.SusceptibilityResultService;

import java.net.URI;

@RestController
@RequestMapping("/results")
public class SusceptibilityResultController {

    private final SusceptibilityResultService resultService;

    public SusceptibilityResultController(SusceptibilityResultService resultService) {
        this.resultService = resultService;
    }

    /** Un técnico de laboratorio solo agrega resultados a aislamientos de sus establecimientos. */
    @PostMapping
    @PreAuthorize("@facilityAccess.canAccessIsolate(authentication, #dto.isolateId())")
    public ResponseEntity<SusceptibilityResultResponseDTO> create(
            @Valid @RequestBody CreateSusceptibilityResultDTO dto,
            UriComponentsBuilder uriBuilder) {
        SusceptibilityResultResponseDTO created = resultService.create(dto);
        URI location = uriBuilder.path("/isolates/{id}/results")
                .buildAndExpand(created.isolateId()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        resultService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
