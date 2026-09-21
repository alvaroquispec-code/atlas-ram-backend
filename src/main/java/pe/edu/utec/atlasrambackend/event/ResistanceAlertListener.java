package pe.edu.utec.atlasrambackend.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.edu.utec.atlasrambackend.service.MailService;

@Component
public class ResistanceAlertListener {

    private static final Logger log = LoggerFactory.getLogger(ResistanceAlertListener.class);

    private final MailService mailService;

    public ResistanceAlertListener(MailService mailService) {
        this.mailService = mailService;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener
    public void onResistanceAlert(ResistanceAlertEvent event) {
        log.warn("Alerta: {} frente a {} con {}% de resistencia en {} (n={})",
                event.microorganismName(), event.antibioticName(),
                event.resistancePercentage(), event.facilityName(),
                event.totalTested());

        mailService.sendResistanceAlert(event);
    }
}

