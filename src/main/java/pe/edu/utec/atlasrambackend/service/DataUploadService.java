package pe.edu.utec.atlasrambackend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.dto.DataUploadResponseDTO;
import pe.edu.utec.atlasrambackend.dto.UploadRevertResponseDTO;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.exception.UploadNotRevertableException;
import pe.edu.utec.atlasrambackend.mapper.DataUploadMapper;
import pe.edu.utec.atlasrambackend.model.DataUpload;
import pe.edu.utec.atlasrambackend.model.ResistanceAlert;
import pe.edu.utec.atlasrambackend.model.UploadStatus;
import pe.edu.utec.atlasrambackend.model.User;
import pe.edu.utec.atlasrambackend.repository.DataUploadRepository;
import pe.edu.utec.atlasrambackend.repository.IsolateRepository;
import pe.edu.utec.atlasrambackend.repository.SusceptibilityResultRepository;
import pe.edu.utec.atlasrambackend.repository.UserRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Service
public class DataUploadService {

    private final DataUploadRepository dataUploadRepository;
    private final IsolateRepository isolateRepository;
    private final SusceptibilityResultRepository resultRepository;
    private final UserRepository userRepository;
    private final AlertService alertService;
    private final DataUploadMapper dataUploadMapper;

    public DataUploadService(DataUploadRepository dataUploadRepository,
                             IsolateRepository isolateRepository,
                             SusceptibilityResultRepository resultRepository,
                             UserRepository userRepository,
                             AlertService alertService,
                             DataUploadMapper dataUploadMapper) {
        this.dataUploadRepository = dataUploadRepository;
        this.isolateRepository = isolateRepository;
        this.resultRepository = resultRepository;
        this.userRepository = userRepository;
        this.alertService = alertService;
        this.dataUploadMapper = dataUploadMapper;
    }

    /** allowedFacilityIds null = sin restricción (ADMIN); si no, solo las cargas de esos establecimientos. */
    @Transactional(readOnly = true)
    public Page<DataUploadResponseDTO> findAll(Pageable pageable, Collection<Long> allowedFacilityIds) {
        Page<DataUpload> page;
        if (allowedFacilityIds == null) {
            page = dataUploadRepository.findAll(pageable);
        } else if (allowedFacilityIds.isEmpty()) {
            page = Page.empty(pageable);
        } else {
            page = dataUploadRepository.findByFacilityIdIn(allowedFacilityIds, pageable);
        }
        return page.map(dataUploadMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public DataUploadResponseDTO findById(Long id) {
        return dataUploadMapper.toResponse(dataUploadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Carga", id)));
    }

    @Transactional(readOnly = true)
    public List<DataUploadResponseDTO> findByStatus(UploadStatus status, Collection<Long> allowedFacilityIds) {
        List<DataUpload> uploads;
        if (allowedFacilityIds == null) {
            uploads = dataUploadRepository.findByStatus(status);
        } else if (allowedFacilityIds.isEmpty()) {
            uploads = List.of();
        } else {
            uploads = dataUploadRepository.findByStatusAndFacilityIdIn(status, allowedFacilityIds);
        }
        return uploads.stream().map(dataUploadMapper::toResponse).toList();
    }

    /** Aislamientos que entraron por esa carga; útil antes de revertirla. */
    @Transactional(readOnly = true)
    public long countIsolates(Long uploadId) {
        if (!dataUploadRepository.existsById(uploadId)) {
            throw new ResourceNotFoundException("Carga", uploadId);
        }
        return isolateRepository.countByDataUploadId(uploadId);
    }

    /**
     * Deshace una carga: borra sus resultados y aislamientos, deja constancia de quién, cuándo y por qué,
     * y revisa las alertas del establecimiento. Todo en una transacción: o se revierte completa o nada.
     * La fila de la carga se conserva como registro de auditoría.
     */
    @Transactional
    public UploadRevertResponseDTO revert(Long id, String reason, String adminEmail) {
        DataUpload upload = dataUploadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Carga", id));

        switch (upload.getStatus()) {
            case PENDING, PROCESSING -> throw new UploadNotRevertableException(
                    "La carga " + id + " aún se está procesando; espere a que termine para revertirla");
            case REVERTED -> throw new UploadNotRevertableException(
                    "La carga " + id + " ya fue revertida el " + upload.getRevertedAt());
            case COMPLETED, FAILED -> {
                // Una carga fallida también puede haber dejado datos si el error ocurrió al final.
            }
        }

        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", adminEmail));

        // Primero los resultados: dependen de los aislamientos por llave foránea.
        int resultsRemoved = resultRepository.deleteByUploadId(id);
        int isolatesRemoved = isolateRepository.deleteByUploadId(id);

        Instant now = Instant.now();
        upload.setStatus(UploadStatus.REVERTED);
        upload.setRevertedAt(now);
        upload.setRevertedBy(admin);
        upload.setRevertReason(reason.trim());

        List<Long> withdrawn = alertService.reassessAfterRevert(upload.getFacility(), id).stream()
                .map(ResistanceAlert::getId)
                .toList();

        return new UploadRevertResponseDTO(dataUploadMapper.toResponse(upload),
                isolatesRemoved, resultsRemoved, withdrawn);
    }
}
