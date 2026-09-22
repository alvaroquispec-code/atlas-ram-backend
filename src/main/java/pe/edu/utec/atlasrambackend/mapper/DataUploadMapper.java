package pe.edu.utec.atlasrambackend.mapper;

import org.springframework.stereotype.Component;
import pe.edu.utec.atlasrambackend.dto.DataUploadResponseDTO;
import pe.edu.utec.atlasrambackend.model.DataUpload;

@Component
public class DataUploadMapper {

    public DataUploadResponseDTO toResponse(DataUpload u) {
        return new DataUploadResponseDTO(
                u.getId(),
                u.getFileName(),
                u.getStatus(),
                u.getTotalRows(),
                u.getProcessedRows(),
                u.getFailedRows(),
                u.getErrorLog(),
                u.getStartedAt(),
                u.getFinishedAt()
        );
    }
}