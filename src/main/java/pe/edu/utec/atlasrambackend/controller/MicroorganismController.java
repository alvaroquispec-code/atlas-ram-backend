
package pe.edu.utec.atlasrambackend.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import pe.edu.utec.atlasrambackend.dto.CreateMicroorganismDTO;
import pe.edu.utec.atlasrambackend.dto.MicroorganismResponseDTO;
import pe.edu.utec.atlasrambackend.service.MicroorganismService;

import java.net.URI;

@RestController
@RequestMapping("/microorganisms")
public class MicroorganismController {

    private final MicroorganismService microorganismService;

    public MicroorganismController(MicroorganismService microorganismService) {
        this.microorganismService = microorganismService;
    }

    @GetMapping
    public ResponseEntity<Page<MicroorganismResponseDTO>> findAll(
            @PageableDefault(size = 20, sort = "code") Pageable pageable) {
        return ResponseEntity.ok(microorganismService.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MicroorganismResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(microorganismService.findById(id));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<MicroorganismResponseDTO> findByCode(@PathVariable String code) {
        return ResponseEntity.ok(microorganismService.findByCode(code));
    }

    @PostMapping
    public ResponseEntity<MicroorganismResponseDTO> create(@Valid @RequestBody CreateMicroorganismDTO dto,
                                                           UriComponentsBuilder uriBuilder) {
        MicroorganismResponseDTO created = microorganismService.create(dto);
        URI location = uriBuilder.path("/microorganisms/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MicroorganismResponseDTO> update(@PathVariable Long id,
                                                           @Valid @RequestBody CreateMicroorganismDTO dto) {
        return ResponseEntity.ok(microorganismService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        microorganismService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
