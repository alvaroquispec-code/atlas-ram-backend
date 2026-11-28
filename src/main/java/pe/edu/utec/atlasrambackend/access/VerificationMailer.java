package pe.edu.utec.atlasrambackend.access;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envío del correo de verificación de cuenta. Usa el mismo SMTP que los avisos de carga (MAIL_ENABLED,
 * MAIL_HOST, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM). Si el envío está apagado o falla, devuelve false: la
 * cuenta queda sin verificar y la aprobación automática no se activa (nunca se "verifica" sin prueba).
 * El enlace (que lleva el token) nunca se escribe en los logs.
 */
@Component
public class VerificationMailer {

    private static final Logger log = LoggerFactory.getLogger(VerificationMailer.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final String from;
    private final boolean enabled;

    public VerificationMailer(ObjectProvider<JavaMailSender> sender,
                              @Value("${app.mail.from:}") String from,
                              @Value("${app.mail.enabled:false}") boolean enabled) {
        this.sender = sender;
        this.from = from;
        this.enabled = enabled;
    }

    /** Devuelve true si el correo salió. Un fallo de envío no rompe el registro: se puede reenviar. */
    public boolean send(String to, String subject, String body) {
        JavaMailSender mail = sender.getIfAvailable();
        if (!enabled || mail == null || from.isBlank()) {
            log.warn("Correo de verificación no enviado a {}: envío de correos apagado (MAIL_ENABLED)", to);
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mail.send(message);
            return true;
        } catch (RuntimeException e) {
            log.warn("No se pudo enviar el correo de verificación a {}: {}", to, e.getMessage());
            return false;
        }
    }
}
