package pe.edu.utec.atlasrambackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import pe.edu.utec.atlasrambackend.event.ResistanceAlertEvent;
import pe.edu.utec.atlasrambackend.event.UploadCompletedEvent;
import pe.edu.utec.atlasrambackend.event.UploadFailedEvent;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final boolean enabled;

    public MailService(JavaMailSender mailSender,
                       @Value("${app.mail.from}") String from,
                       @Value("${app.mail.enabled}") boolean enabled) {
        this.mailSender = mailSender;
        this.from = from;
        this.enabled = enabled;
    }

    public void sendUploadCompleted(UploadCompletedEvent event) {
        String subject = "Carga de datos completada: " + event.fileName();
        String body = """
                Hola %s,

                Tu carga del archivo %s terminó de procesarse.

                Filas totales:     %d
                Procesadas:        %d
                Con error:         %d

                Puedes consultar el detalle en la plataforma.

                Atlas RAM
                """.formatted(event.userFullName(), event.fileName(),
                event.totalRows(), event.processedRows(), event.failedRows());

        send(event.userEmail(), subject, body);
    }

    public void sendUploadFailed(UploadFailedEvent event) {
        String subject = "Error al procesar: " + event.fileName();
        String body = """
                Hola %s,

                Tu carga del archivo %s no pudo completarse.

                Motivo: %s

                Ningún dato fue registrado. Corrige el archivo e inténtalo de nuevo.

                Atlas RAM
                """.formatted(event.userFullName(), event.fileName(), event.reason());

        send(event.userEmail(), subject, body);
    }

    public void sendResistanceAlert(ResistanceAlertEvent event) {
        String subject = "Alerta de resistencia: %s en %s"
                .formatted(event.microorganismName(), event.facilityName());
        String body = """
                Se detectó un nivel de resistencia que supera el umbral configurado.

                Establecimiento:   %s
                Microorganismo:    %s
                Antibiótico:       %s
                Resistencia:       %.1f%%
                Aislamientos:      %d

                Revisa el antibiograma acumulado en la plataforma.

                Atlas RAM
                """.formatted(event.facilityName(), event.microorganismName(),
                event.antibioticName(), event.resistancePercentage(),
                event.totalTested());

        send("epidemiologo@atlasram.pe", subject, body);
    }

    private void send(String to, String subject, String body) {
        if (!enabled) {
            log.info("Correo no enviado (app.mail.enabled=false). Para: {} | Asunto: {}", to, subject);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Correo enviado a {}", to);
        } catch (Exception e) {
            log.error("No se pudo enviar el correo a {}: {}", to, e.getMessage());
        }
    }
}

