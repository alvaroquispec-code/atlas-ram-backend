package pe.edu.utec.atlasrambackend.event;

public record UploadFailedEvent(
        Long uploadId,
        String fileName,
        String userEmail,
        String userFullName,
        String reason
) {}

