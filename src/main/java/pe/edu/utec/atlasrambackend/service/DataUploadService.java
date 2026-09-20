package pe.edu.utec.atlasrambackend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.dto.DataUploadResponseDTO;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.mapper.DataUploadMapper;
import pe.edu.utec.atlasrambackend.model.UploadStatus;
import pe.edu.utec.atlasrambackend.repository.DataUploadRepository;
import pe.edu.utec.atlasrambackend.repository.IsolateRepository;

import java.util.List;

@Service
public class DataUploadService {

    private final DataUploadRepository dataUploadRepository;
    private final IsolateRepository isolateRepository;
    private final DataUploadMapper dataUploadMapper;

    public DataUploadService(DataUploadRepository dataUploadRepository,
                             IsolateRepository isolateRepository,
                             DataUploadMapper dataUploadMapper) {
        this.dataUploadRepository = dataUploadRepository;
        this.isolateRepository = isolateRepository;
        this.dataUploadMapper = dataUploadMapper;
    }

    @Transactional(readOnly = true)
    public Page<DataUploadResponseDTO> findAll(Pageable pageable) {
        return dataUploadRepository.findAll(pageable).map(dataUploadMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public DataUploadResponseDTO findById(Long id) {
        return dataUploadMapper.toResponse(dataUploadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Carga", id)));
    }

    @Transactional(readOnly = true)
    public List<DataUploadResponseDTO> findByStatus(UploadStatus status) {
        return dataUploadRepository.findByStatus(status).stream()
                .map(dataUploadMapper::toResponse)
                .toList();
    }

    /** Aislamientos que entraron por esa carga; útil antes de revertirla. */
    @Transactional(readOnly = true)
    public long countIsolates(Long uploadId) {
        if (!dataUploadRepository.existsById(uploadId)) {
            throw new ResourceNotFoundException("Carga", uploadId);
        }
        return isolateRepository.countByDataUploadId(uploadId);
    }
}
