package pe.edu.utec.atlasrambackend.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.event.EventPublisher;
import pe.edu.utec.atlasrambackend.event.UploadFailedEvent;
import pe.edu.utec.atlasrambackend.model.DataUpload;
import pe.edu.utec.atlasrambackend.model.UploadStatus;
import pe.edu.utec.atlasrambackend.repository.DataUploadRepository;

import java.time.Instant;

/**
 * Cambios de estado de una carga, cada uno en su propia transacción (REQUIRES_NEW).
 *
 * PROCESSING se confirma antes de importar, así se ve mientras dura y el revert lo respeta.
 * FAILED se escribe después de que la importación se deshizo: si se guardara dentro de la misma
 * transacción y el error fue de la base de datos, PostgreSQL la habría abortado y el FAILED también
 * se perdería, dejando la carga en PENDING para siempre.
 */
@Component
public class UploadStatusWriter {

    private final DataUploadRepository uploadRepository;
    private final EventPublisher eventPublisher;

    public UploadStatusWriter(DataUploadRepository uploadRepository, EventPublisher eventPublisher) {
        this.uploadRepository = uploadRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(Long uploadId) {
        DataUpload upload = uploadRepository.findById(uploadId).orElseThrow();
        upload.setStatus(UploadStatus.PROCESSING);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long uploadId, String reason) {
        DataUpload upload = uploadRepository.findById(uploadId).orElseThrow();
        upload.setStatus(UploadStatus.FAILED);
        upload.setFinishedAt(Instant.now());
        upload.setErrorLog(reason);
        // Se publica dentro de esta transacción: el listener (AFTER_COMMIT) solo avisa si el FAILED quedó guardado.
        eventPublisher.publish(new UploadFailedEvent(
                upload.getId(), upload.getFileName(),
                upload.getUploadedBy().getEmail(),
                upload.getUploadedBy().getFullName(),
                reason));
    }
}
