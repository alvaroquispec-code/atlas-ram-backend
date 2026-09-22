package pe.edu.utec.atlasrambackend.event;

public record UploadCompletedEvent(
        Long uploadId,
        String fileName,
        String userEmail,
        String userFullName,
        int totalRows,
        int processedRows,
        int failedRows
) {}
