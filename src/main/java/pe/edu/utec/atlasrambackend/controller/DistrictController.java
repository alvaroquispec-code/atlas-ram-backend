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
import pe.edu.utec.atlasrambackend.dto.CreateDistrictDTO;
import pe.edu.utec.atlasrambackend.dto.DistrictResponseDTO;
import pe.edu.utec.atlasrambackend.service.DistrictService;


import java.net.URI;


@RestController
@RequestMapping("/districts")
public class DistrictController {


    private final DistrictService districtService;


    public DistrictController(DistrictService districtService) {
        this.districtService = districtService;
    }


    @GetMapping
    public ResponseEntity<Page<DistrictResponseDTO>> findAll(
            @PageableDefault(size = 20, sort = "ubigeo") Pageable pageable) {
        return ResponseEntity.ok(districtService.findAll(pageable));
    }


    @GetMapping("/{id}")
    public ResponseEntity<DistrictResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(districtService.findById(id));
    }


    @GetMapping("/ubigeo/{ubigeo}")
    public ResponseEntity<DistrictResponseDTO> findByUbigeo(@PathVariable String ubigeo) {
        return ResponseEntity.ok(districtService.findByUbigeo(ubigeo));
    }


    @PostMapping
    public ResponseEntity<DistrictResponseDTO> create(@Valid @RequestBody CreateDistrictDTO dto,
                                                      UriComponentsBuilder uriBuilder) {
        DistrictResponseDTO created = districtService.create(dto);
        URI location = uriBuilder.path("/districts/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }


    @PutMapping("/{id}")
    public ResponseEntity<DistrictResponseDTO> update(@PathVariable Long id,
                                                      @Valid @RequestBody CreateDistrictDTO dto) {
        return ResponseEntity.ok(districtService.update(id, dto));
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        districtService.delete(id);
        return ResponseEntity.noContent().build();
    }
}




