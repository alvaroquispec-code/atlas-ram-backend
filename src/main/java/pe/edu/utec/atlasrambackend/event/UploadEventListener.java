package pe.edu.utec.atlasrambackend.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.edu.utec.atlasrambackend.service.MailService;

@Component
public class UploadEventListener {

    private static final Logger log = LoggerFactory.getLogger(UploadEventListener.class);

    private final MailService mailService;

    public UploadEventListener(MailService mailService) {
        this.mailService = mailService;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener
    public void onUploadCompleted(UploadCompletedEvent event) {
        log.info("Carga {} completada: {} de {} filas procesadas",
                event.uploadId(), event.processedRows(), event.totalRows());

        mailService.sendUploadCompleted(event);
    }

    @Async("notificationExecutor")
    @TransactionalEventListener
    public void onUploadFailed(UploadFailedEvent event) {
        log.warn("Carga {} falló: {}", event.uploadId(), event.reason());

        mailService.sendUploadFailed(event);
    }
}

