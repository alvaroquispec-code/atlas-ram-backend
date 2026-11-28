package pe.edu.utec.atlasrambackend.access;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.model.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Verificación de correo: se envía un enlace de un solo uso que vence en 24 horas. Se guarda el SHA-256 del
 * token (si alguien lee la tabla, no puede verificar cuentas ajenas). Al verificar, se intenta la
 * aprobación automática de sus solicitudes de acceso profesional.
 */
@Service
public class EmailVerificationService {

    static final Duration VALIDITY = Duration.ofHours(24);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailVerificationRepository repository;
    private final VerificationMailer mailService;
    private final AccessRequestService accessRequestService;
    private final String webUrl;

    public EmailVerificationService(EmailVerificationRepository repository, VerificationMailer mailService,
                                    AccessRequestService accessRequestService,
                                    @Value("${app.web-url:https://atlas-ram-web.vercel.app}") String webUrl) {
        this.repository = repository;
        this.mailService = mailService;
        this.accessRequestService = accessRequestService;
        this.webUrl = webUrl.replaceAll("/+$", "");
    }

    /** Crea un enlace nuevo y lo envía. Devuelve true si el correo salió. */
    @Transactional
    public boolean sendLink(User user) {
        if (user.isEmailVerified()) {
            return false;
        }
        if (user.getId() != null
                && repository.existsByUserIdAndExpiresAtAfter(user.getId(), Instant.now().plus(VALIDITY).minusSeconds(60))) {
            return false;
        }
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        EmailVerification v = new EmailVerification();
        v.setTokenHash(sha256(token));
        v.setUser(user);
        v.setExpiresAt(Instant.now().plus(VALIDITY));
        repository.save(v);
        String link = webUrl + "/verificar-correo?token=" + token;
        return mailService.send(user.getEmail(), "Confirma tu correo en Atlas RAM",
                "Hola " + user.getFullName() + ":\n\n"
                        + "Para confirmar tu correo en Atlas RAM, abre este enlace (vence en 24 horas):\n\n"
                        + link + "\n\n"
                        + "Si usaste tu correo institucional y pediste acceso profesional, al confirmarlo recibirás "
                        + "el rol de tu establecimiento.\n\n"
                        + "Si no creaste esta cuenta, ignora este mensaje.\n\n— Atlas RAM");
    }

    /** Marca el correo como verificado y prueba la aprobación automática. Devuelve el usuario verificado. */
    @Transactional
    public User verify(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessRuleException("Enlace de verificación inválido");
        }
        EmailVerification v = repository.findByTokenHash(sha256(token.trim()))
                .orElseThrow(() -> new BusinessRuleException("Enlace de verificación inválido o ya usado"));
        if (v.getUsedAt() != null) {
            throw new BusinessRuleException("Este enlace ya se usó");
        }
        if (v.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessRuleException("El enlace venció: pide uno nuevo desde Mi cuenta");
        }
        v.setUsedAt(Instant.now());
        User user = v.getUser();
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(Instant.now());
        }
        accessRequestService.autoApprovePending(user);
        return user;
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
