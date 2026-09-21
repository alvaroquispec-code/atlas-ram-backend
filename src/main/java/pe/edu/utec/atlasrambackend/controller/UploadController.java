package pe.edu.utec.atlasrambackend.controller;

import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.utec.atlasrambackend.dto.DataUploadResponseDTO;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.mapper.DataUploadMapper;
import pe.edu.utec.atlasrambackend.model.DataUpload;
import pe.edu.utec.atlasrambackend.model.Facility;
import pe.edu.utec.atlasrambackend.model.User;
import pe.edu.utec.atlasrambackend.repository.DataUploadRepository;
import pe.edu.utec.atlasrambackend.repository.FacilityRepository;
import pe.edu.utec.atlasrambackend.repository.UserRepository;
import pe.edu.utec.atlasrambackend.service.UploadService;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/uploads")
public class UploadController {

    private final UploadService uploadService;
    private final DataUploadRepository uploadRepository;
    private final UserRepository userRepository;
    private final FacilityRepository facilityRepository;
    private final DataUploadMapper mapper;

    public UploadController(UploadService uploadService,
                            DataUploadRepository uploadRepository,
                            UserRepository userRepository,
                            FacilityRepository facilityRepository,
                            DataUploadMapper mapper) {
        this.uploadService = uploadService;
        this.uploadRepository = uploadRepository;
        this.userRepository = userRepository;
        this.facilityRepository = facilityRepository;
        this.mapper = mapper;
    }

    @PostMapping
    @PreAuthorize("@facilityAccess.canAccess(authentication, #facilityId)")
    public ResponseEntity<DataUploadResponseDTO> upload(
            @RequestParam("file") @NotNull MultipartFile file,
            @RequestParam("facilityId") Long facilityId,
            @AuthenticationPrincipal UserDetails principal) throws IOException {

        if (file.isEmpty()) {
            throw new BusinessRuleException("El archivo está vacío");
        }

        User user = userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", principal.getUsername()));

        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Establecimiento", facilityId));

        DataUpload upload = new DataUpload();
        upload.setFileName(file.getOriginalFilename());
        upload.setUploadedBy(user);
        upload.setFacility(facility);

        DataUpload saved = uploadRepository.save(upload);

        uploadService.process(saved.getId(), file.getBytes());

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(mapper.toResponse(saved));
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DataUploadResponseDTO>> getActive() {
        List<DataUploadResponseDTO> active = uploadRepository.findByFinishedAtIsNull()
                .stream().map(mapper::toResponse).toList();
        return ResponseEntity.ok(active);
    }
}
