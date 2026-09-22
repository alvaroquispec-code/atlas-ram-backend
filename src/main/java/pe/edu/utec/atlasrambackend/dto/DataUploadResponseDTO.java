package pe.edu.utec.atlasrambackend.dto;

import pe.edu.utec.atlasrambackend.model.UploadStatus;

import java.time.Instant;

public record DataUploadResponseDTO(
        Long id,
        String fileName,
        UploadStatus status,
        Integer totalRows,
        Integer processedRows,
        Integer failedRows,
        String errorLog,
        Instant startedAt,
        Instant finishedAt
) {}