package pe.edu.utec.atlasrambackend.controller;

import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.utec.atlasrambackend.config.FacilityAccessEvaluator;
import pe.edu.utec.atlasrambackend.dto.DataUploadResponseDTO;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.exception.DuplicateResourceException;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.mapper.DataUploadMapper;
import pe.edu.utec.atlasrambackend.model.DataUpload;
import pe.edu.utec.atlasrambackend.model.Facility;
import pe.edu.utec.atlasrambackend.model.UploadStatus;
import pe.edu.utec.atlasrambackend.model.User;
import pe.edu.utec.atlasrambackend.repository.DataUploadRepository;
import pe.edu.utec.atlasrambackend.repository.FacilityRepository;
import pe.edu.utec.atlasrambackend.repository.UserRepository;
import pe.edu.utec.atlasrambackend.service.CsvParser;
import pe.edu.utec.atlasrambackend.service.FileFingerprint;
import pe.edu.utec.atlasrambackend.service.UploadService;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/uploads")
public class UploadController {

    /** Estados en los que una carga "ocupa" su archivo: fallida o revertida, se puede volver a subir. */
    private static final Set<UploadStatus> ACTIVE =
            EnumSet.of(UploadStatus.PENDING, UploadStatus.PROCESSING, UploadStatus.COMPLETED);

    private final UploadService uploadService;
    private final DataUploadRepository uploadRepository;
    private final UserRepository userRepository;
    private final FacilityRepository facilityRepository;
    private final DataUploadMapper mapper;
    private final FacilityAccessEvaluator facilityAccess;
    private final CsvParser csvParser;

    public UploadController(UploadService uploadService,
                            DataUploadRepository uploadRepository,
                            UserRepository userRepository,
                            FacilityRepository facilityRepository,
                            DataUploadMapper mapper,
                            FacilityAccessEvaluator facilityAccess,
                            CsvParser csvParser) {
        this.uploadService = uploadService;
        this.uploadRepository = uploadRepository;
        this.userRepository = userRepository;
        this.facilityRepository = facilityRepository;
        this.mapper = mapper;
        this.facilityAccess = facilityAccess;
        this.csvParser = csvParser;
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

        // Idempotencia: el mismo archivo subido dos veces (doble clic, reintento) no duplica los datos.
        byte[] content = file.getBytes();
        String sha256 = FileFingerprint.sha256(content);
        uploadRepository.findFirstByFacilityIdAndFileSha256AndStatusIn(facilityId, sha256, ACTIVE)
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("Este archivo ya se cargó en este establecimiento (carga "
                            + existing.getId() + ", " + existing.getStatus() + "). Si fue un error, un "
                            + "administrador puede revertirla y luego se vuelve a subir.");
                });

        // La cabecera y el formato se validan aquí: un archivo ilegible responde 400 al instante y no deja
        // una carga FAILED. Los errores de cada fila se informan después, en el registro de la carga.
        CsvParser.Result parsed = csvParser.parse(content);

        DataUpload upload = new DataUpload();
        upload.setFileName(file.getOriginalFilename());
        upload.setUploadedBy(user);
        upload.setFacility(facility);
        upload.setFileSha256(sha256);

        DataUpload saved = uploadRepository.save(upload);

        uploadService.process(saved.getId(), parsed);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(mapper.toResponse(saved));
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DataUploadResponseDTO>> getActive(Authentication authentication) {
        Set<Long> allowed = facilityAccess.visibleFacilityIds(authentication).orElse(null);
        List<DataUpload> uploads = allowed == null
                ? uploadRepository.findByFinishedAtIsNull()
                : allowed.isEmpty() ? List.<DataUpload>of() : uploadRepository.findByFinishedAtIsNullAndFacilityIdIn(allowed);
        List<DataUploadResponseDTO> active = uploads.stream().map(mapper::toResponse).toList();
        return ResponseEntity.ok(active);
    }
}
