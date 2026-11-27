package pe.edu.utec.atlasrambackend.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.edu.utec.atlasrambackend.exception.TooManyAttemptsException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de intentos de inicio de sesión contra fuerza bruta (OWASP API Security Top 10 2023, API2 y API4).
 *
 * Solo cuentan los intentos FALLIDOS, en una ventana deslizante, por dos claves:
 *   - por correo: protege cada cuenta aunque el atacante cambie de IP;
 *   - por IP: frena a quien prueba muchas cuentas desde el mismo lugar.
 * Un inicio de sesión correcto nunca se bloquea por esto (salvo que esa cuenta ya esté bloqueada) y borra
 * el contador de su correo, así que muchos usuarios legítimos detrás de la misma IP (una sala, una
 * universidad) no se afectan entre sí.
 *
 * Es memoria del proceso: con una sola instancia del backend basta. Con varias habría que compartirlo
 * (por ejemplo, en Redis). Se apaga con app.rate-limit.enabled=false (variable RATE_LIMIT_ENABLED).
 */
@Component
public class LoginAttemptLimiter {

    /** Entradas a partir de las cuales se limpian las claves vencidas (evita crecer sin límite). */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final boolean enabled;
    private final int maxPerEmail;
    private final int maxPerIp;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    /**
     * Secreto que CloudFront agrega a cada petición (cabecera X-Origin-Verify) cuando la API se sirve detrás
     * de CloudFront. Solo si llega ese secreto se confía en CloudFront-Viewer-Address: así nadie que llame
     * directo al servidor puede inventarse una IP. Vacío (por defecto): no se usa CloudFront.
     */
    private static volatile String cloudFrontSecret = "";

    @Autowired
    public LoginAttemptLimiter(@Value("${app.rate-limit.enabled:true}") boolean enabled,
                               @Value("${app.rate-limit.max-failures-per-email:5}") int maxPerEmail,
                               @Value("${app.rate-limit.max-failures-per-ip:30}") int maxPerIp,
                               @Value("${app.rate-limit.window-minutes:15}") int windowMinutes,
                               @Value("${app.cloudfront.origin-secret:}") String cloudFrontOriginSecret) {
        this(enabled, maxPerEmail, maxPerIp, Duration.ofMinutes(windowMinutes), Clock.systemUTC());
        setCloudFrontSecret(cloudFrontOriginSecret);
    }

    static void setCloudFrontSecret(String secret) {
        cloudFrontSecret = secret == null ? "" : secret.trim();
    }

    LoginAttemptLimiter(boolean enabled, int maxPerEmail, int maxPerIp, Duration window, Clock clock) {
        this.enabled = enabled;
        this.maxPerEmail = maxPerEmail;
        this.maxPerIp = maxPerIp;
        this.window = window;
        this.clock = clock;
    }

    /** Lanza TooManyAttemptsException si el correo o la IP superaron el límite de intentos fallidos. */
    public void checkAllowed(String email, String ip) {
        if (!enabled) {
            return;
        }
        Instant now = clock.instant();
        long wait = Math.max(retryAfter(emailKey(email), maxPerEmail, now), retryAfter(ipKey(ip), maxPerIp, now));
        if (wait > 0) {
            throw new TooManyAttemptsException(wait);
        }
    }

    public void recordFailure(String email, String ip) {
        if (!enabled) {
            return;
        }
        Instant now = clock.instant();
        add(emailKey(email), now);
        add(ipKey(ip), now);
        if (failures.size() > CLEANUP_THRESHOLD) {
            failures.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    prune(e.getValue(), now);
                    return e.getValue().isEmpty();
                }
            });
        }
    }

    /** Un inicio de sesión correcto borra los fallos de ese correo (no los de la IP). */
    public void recordSuccess(String email) {
        failures.remove(emailKey(email));
    }

    /**
     * IP del cliente. Detrás de Caddy la conexión viene del proxy, que agrega la IP real al final de
     * X-Forwarded-For; se toma el último valor, el que puso el proxy (los anteriores los puede inventar el
     * cliente).
     */
    public static String clientIp(HttpServletRequest request) {
        String viewer = cloudFrontViewerIp(request);
        if (viewer != null) {
            return viewer;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Detrás de CloudFront, Caddy ve la IP del borde de CloudFront, no la del usuario. CloudFront manda la
     * del usuario en CloudFront-Viewer-Address ("ip:puerto"). Solo se usa si la petición trae el secreto.
     */
    static String cloudFrontViewerIp(HttpServletRequest request) {
        String secret = cloudFrontSecret;
        String received = request.getHeader("X-Origin-Verify");
        if (secret.isEmpty() || received == null || !MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8), received.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        String viewer = request.getHeader("CloudFront-Viewer-Address");
        if (viewer == null || viewer.isBlank()) {
            return null;
        }
        int colon = viewer.lastIndexOf(':');
        return (colon > 0 ? viewer.substring(0, colon) : viewer).trim();
    }

    private void add(String key, Instant now) {
        Deque<Instant> deque = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            prune(deque, now);
            deque.addLast(now);
        }
    }

    /** Segundos que faltan para poder intentar de nuevo; 0 si no está bloqueado. */
    private long retryAfter(String key, int max, Instant now) {
        Deque<Instant> deque = failures.get(key);
        if (deque == null) {
            return 0;
        }
        synchronized (deque) {
            prune(deque, now);
            if (deque.size() < max) {
                return 0;
            }
            // Se libera cuando vence el fallo que hace que se alcance el límite.
            Instant oldestCounting = deque.stream().skip(deque.size() - max).findFirst().orElse(now);
            return Math.max(1, Duration.between(now, oldestCounting.plus(window)).toSeconds());
        }
    }

    private void prune(Deque<Instant> deque, Instant now) {
        Instant limit = now.minus(window);
        while (!deque.isEmpty() && !deque.peekFirst().isAfter(limit)) {
            deque.pollFirst();
        }
    }

    private static String emailKey(String email) {
        return "email:" + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
    }

    private static String ipKey(String ip) {
        return "ip:" + (ip == null ? "" : ip);
    }
}
