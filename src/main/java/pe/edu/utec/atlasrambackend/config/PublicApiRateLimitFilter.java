package pe.edu.utec.atlasrambackend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Límite de peticiones por IP para quien consulta SIN sesión (anti-scraping; OWASP API Security Top 10
 * 2023, API4: consumo de recursos sin restricción).
 *
 * Los agregados públicos (mapa, antibiograma, tendencias) son abiertos a propósito, pero un robot que los
 * descarga en bucle encarece el servidor y la base de datos. Se cuenta por IP en ventanas de un minuto;
 * al pasar el límite se responde 429 con Retry-After.
 *
 * No se cuentan:
 *   - las peticiones con sesión (cabecera Authorization): son usuarios identificados y auditados;
 *   - las que salen de la propia máquina (127.0.0.1, ::1), como las pruebas automáticas.
 * Detrás de Caddy la IP real se toma de X-Forwarded-For, igual que en LoginAttemptLimiter.
 *
 * El límite por defecto (600 por minuto) es holgado: una sala entera detrás de la misma IP puede abrir el
 * tablero a la vez sin tocarlo. Se ajusta con API_RATE_LIMIT_PER_MINUTE y se apaga con 0 o con
 * RATE_LIMIT_ENABLED=false. Es memoria del proceso: con varias instancias iría en Redis o en AWS WAF.
 */
@Component
public class PublicApiRateLimitFilter extends OncePerRequestFilter {

    /** Entradas a partir de las cuales se borran las ventanas viejas. */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final boolean enabled;
    private final int limitPerMinute;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private record Window(long minute, AtomicInteger count) {
    }

    public PublicApiRateLimitFilter(@Value("${app.rate-limit.enabled:true}") boolean enabled,
                                    @Value("${app.rate-limit.public-per-minute:600}") int limitPerMinute) {
        this.enabled = enabled && limitPerMinute > 0;
        this.limitPerMinute = limitPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || request.getHeader(HttpHeaders.AUTHORIZATION) != null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = LoginAttemptLimiter.clientIp(request);
        if (isLoopback(ip)) {
            chain.doFilter(request, response);
            return;
        }
        long now = Instant.now().getEpochSecond();
        long minute = now / 60;
        Window window = windows.compute(ip, (key, current) ->
                current == null || current.minute() != minute ? new Window(minute, new AtomicInteger()) : current);
        if (window.count().incrementAndGet() > limitPerMinute) {
            long retryAfter = Math.max(1, (minute + 1) * 60 - now);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":"
                    + "\"Demasiadas consultas desde tu red. Espera " + retryAfter + " s o inicia sesión.\"}");
            return;
        }
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.entrySet().removeIf(e -> e.getValue().minute() < minute);
        }
        chain.doFilter(request, response);
    }

    static boolean isLoopback(String ip) {
        return ip == null || ip.equals("127.0.0.1") || ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1");
    }
}
