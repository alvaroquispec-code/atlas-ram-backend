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
import pe.edu.utec.atlasrambackend.dto.CreateAntibioticDTO;
import pe.edu.utec.atlasrambackend.dto.AntibioticResponseDTO;
import pe.edu.utec.atlasrambackend.service.AntibioticService;


import java.net.URI;


@RestController
@RequestMapping("/antibiotics")
public class AntibioticController {


    private final AntibioticService antibioticService;


    public AntibioticController(AntibioticService antibioticService) {
        this.antibioticService = antibioticService;
    }


    @GetMapping
    public ResponseEntity<Page<AntibioticResponseDTO>> findAll(
            @PageableDefault(size = 20, sort = "code") Pageable pageable) {
        return ResponseEntity.ok(antibioticService.findAll(pageable));
    }


    @GetMapping("/{id}")
    public ResponseEntity<AntibioticResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(antibioticService.findById(id));
    }


    @GetMapping("/code/{code}")
    public ResponseEntity<AntibioticResponseDTO> findByCode(@PathVariable String code) {
        return ResponseEntity.ok(antibioticService.findByCode(code));
    }


    @PostMapping
    public ResponseEntity<AntibioticResponseDTO> create(@Valid @RequestBody CreateAntibioticDTO dto,
                                                        UriComponentsBuilder uriBuilder) {
        AntibioticResponseDTO created = antibioticService.create(dto);
        URI location = uriBuilder.path("/antibiotics/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }


    @PutMapping("/{id}")
    public ResponseEntity<AntibioticResponseDTO> update(@PathVariable Long id,
                                                        @Valid @RequestBody CreateAntibioticDTO dto) {
        return ResponseEntity.ok(antibioticService.update(id, dto));
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        antibioticService.delete(id);
        return ResponseEntity.noContent().build();
    }
}


