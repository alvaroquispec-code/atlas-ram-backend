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
import pe.edu.utec.atlasrambackend.dto.CreateFacilityDTO;
import pe.edu.utec.atlasrambackend.dto.FacilityResponseDTO;
import pe.edu.utec.atlasrambackend.service.FacilityService;


import java.net.URI;
import java.util.List;


@RestController
@RequestMapping("/facilities")
public class FacilityController {


    private final FacilityService facilityService;


    public FacilityController(FacilityService facilityService) {
        this.facilityService = facilityService;
    }


    @GetMapping
    public ResponseEntity<Page<FacilityResponseDTO>> findAll(
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(facilityService.findAll(pageable));
    }


    @GetMapping("/{id}")
    public ResponseEntity<FacilityResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(facilityService.findById(id));
    }


    @GetMapping("/district/{districtId}")
    public ResponseEntity<List<FacilityResponseDTO>> findByDistrict(@PathVariable Long districtId) {
        return ResponseEntity.ok(facilityService.findByDistrict(districtId));
    }


    @PostMapping
    public ResponseEntity<FacilityResponseDTO> create(@Valid @RequestBody CreateFacilityDTO dto,
                                                      UriComponentsBuilder uriBuilder) {
        FacilityResponseDTO created = facilityService.create(dto);
        URI location = uriBuilder.path("/facilities/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }


    @PutMapping("/{id}")
    public ResponseEntity<FacilityResponseDTO> update(@PathVariable Long id,
                                                      @Valid @RequestBody CreateFacilityDTO dto) {
        return ResponseEntity.ok(facilityService.update(id, dto));
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        facilityService.delete(id);
        return ResponseEntity.noContent().build();
    }
}



