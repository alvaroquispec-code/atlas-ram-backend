package pe.edu.utec.atlasrambackend.audit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import pe.edu.utec.atlasrambackend.config.LoginAttemptLimiter;

import java.util.List;

/**
 * Registra en audit_log, al terminar cada petición, las escrituras y las lecturas de datos individuales.
 * Corre dentro de Spring MVC, así que ya conoce al usuario autenticado y el código de respuesta.
 * Las lecturas de agregados (antibiograma, mapa, catálogos) no se registran: no exponen pacientes y
 * llenarían la tabla.
 */
@Component
public class AuditInterceptor implements HandlerInterceptor {

    /** Lecturas que exponen registros individuales o datos de usuarios. */
    static final List<String> SENSITIVE_READS = List.of("/isolates", "/results", "/fhir", "/users", "/audit-log");

    private final AuditLog auditLog;

    public AuditInterceptor(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        String path = pathOf(request);
        if (!shouldAudit(request.getMethod(), path)) {
            return;
        }
        auditLog.record(currentUser(), request.getMethod(), path, response.getStatus(),
                LoginAttemptLimiter.clientIp(request));
    }

    static boolean shouldAudit(String method, String path) {
        if ("OPTIONS".equals(method) || "HEAD".equals(method)) {
            return false;
        }
        if (!"GET".equals(method)) {
            return true;
        }
        return SENSITIVE_READS.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    /** Ruta sin el prefijo /api/v1 ni parámetros de consulta (pueden traer datos que no hace falta guardar). */
    static String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }

    private static String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getName();
    }
}
