package pe.edu.utec.atlasrambackend.dto;

import pe.edu.utec.atlasrambackend.model.UploadStatus;

import java.time.Instant;
import pe.edu.utec.atlasrambackend.model.DataOrigin;

public record DataUploadResponseDTO(
        Long id,
        String fileName,
        UploadStatus status,
        Integer totalRows,
        Integer processedRows,
        Integer failedRows,
        String errorLog,
        Instant startedAt,
        Instant finishedAt,
        Instant revertedAt,
        String revertedBy,
        String revertReason,
        DataOrigin dataOrigin,
        /** Completitud de sexo, edad y servicio en los aislamientos de la carga; solo en GET /uploads/{id}. */
        DataCompletenessDTO completeness
) {}