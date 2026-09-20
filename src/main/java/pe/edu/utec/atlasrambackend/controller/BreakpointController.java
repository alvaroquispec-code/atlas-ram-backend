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
import pe.edu.utec.atlasrambackend.dto.BreakpointResponseDTO;
import pe.edu.utec.atlasrambackend.dto.CreateBreakpointDTO;
import pe.edu.utec.atlasrambackend.service.BreakpointService;


import java.net.URI;


@RestController
@RequestMapping("/breakpoints")
public class BreakpointController {


    private final BreakpointService breakpointService;


    public BreakpointController(BreakpointService breakpointService) {
        this.breakpointService = breakpointService;
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



