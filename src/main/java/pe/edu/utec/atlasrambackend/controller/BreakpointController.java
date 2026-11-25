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
import org.springframework.web.bind.annotation.RequestParam;
import pe.edu.utec.atlasrambackend.dto.BreakpointEditionDTO;
import pe.edu.utec.atlasrambackend.dto.BreakpointResponseDTO;
import pe.edu.utec.atlasrambackend.dto.ReinterpretationReportDTO;
import pe.edu.utec.atlasrambackend.service.ReinterpretationService;
import pe.edu.utec.atlasrambackend.dto.CreateBreakpointDTO;
import pe.edu.utec.atlasrambackend.service.BreakpointService;


import java.net.URI;
import java.util.List;


@RestController
@RequestMapping("/breakpoints")
public class BreakpointController {


    private final BreakpointService breakpointService;
    private final ReinterpretationService reinterpretationService;


    public BreakpointController(BreakpointService breakpointService,
                                ReinterpretationService reinterpretationService) {
        this.breakpointService = breakpointService;
        this.reinterpretationService = reinterpretationService;
    }

    /** Ediciones de puntos de corte cargadas y cuál es la activa. */
    @GetMapping("/editions")
    public ResponseEntity<List<BreakpointEditionDTO>> editions() {
        return ResponseEntity.ok(reinterpretationService.editions());
    }

    /**
     * Reinterpreta las CIM y halos guardados con una edición. apply=false (por defecto) es una vista previa
     * que no modifica nada; apply=true guarda, y solo se permite con la edición activa. Solo administrador.
     */
    @PostMapping("/reinterpret")
    public ResponseEntity<ReinterpretationReportDTO> reinterpret(@RequestParam String version,
                                                                 @RequestParam(defaultValue = "false") boolean apply) {
        return ResponseEntity.ok(reinterpretationService.reinterpret(version, apply));
    }


    @GetMapping
    public ResponseEntity<Page<BreakpointResponseDTO>> findAll(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(breakpointService.findAll(pageable));
    }


    @GetMapping("/{id}")
    public ResponseEntity<BreakpointResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(breakpointService.findById(id));
    }


    @PostMapping
    public ResponseEntity<BreakpointResponseDTO> create(@Valid @RequestBody CreateBreakpointDTO dto,
                                                        UriComponentsBuilder uriBuilder) {
        BreakpointResponseDTO created = breakpointService.create(dto);
        URI location = uriBuilder.path("/breakpoints/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }


    @PutMapping("/{id}")
    public ResponseEntity<BreakpointResponseDTO> update(@PathVariable Long id,
                                                        @Valid @RequestBody CreateBreakpointDTO dto) {
        return ResponseEntity.ok(breakpointService.update(id, dto));
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        breakpointService.delete(id);
        return ResponseEntity.noContent().build();
    }
}



