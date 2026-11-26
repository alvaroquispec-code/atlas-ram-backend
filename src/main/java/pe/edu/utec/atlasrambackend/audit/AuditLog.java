package pe.edu.utec.atlasrambackend.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Registro de auditoría (tabla audit_log, V16). Escribe con JDBC directo, fuera de las transacciones de
 * negocio: si el registro falla, la operación del usuario no se cae (se deja constancia en el log).
 */
@Component
public class AuditLog {

    private static final Logger log = LoggerFactory.getLogger(AuditLog.class);
    static final int MAX_PATH = 500;

    private final NamedParameterJdbcTemplate jdbc;

    public AuditLog(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Entry(long id, OffsetDateTime occurredAt, String userEmail, String method, String path,
                        int status, String clientIp) {
    }

    public void record(String userEmail, String method, String path, int status, String clientIp) {
        try {
            jdbc.update("""
                    insert into audit_log (user_email, method, path, status, client_ip)
                    values (:user, :method, :path, :status, :ip)
                    """, new MapSqlParameterSource()
                    .addValue("user", userEmail)
                    .addValue("method", method)
                    .addValue("path", path.length() > MAX_PATH ? path.substring(0, MAX_PATH) : path)
                    .addValue("status", status)
                    .addValue("ip", clientIp));
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar la auditoría de {} {}: {}", method, path, e.getMessage());
        }
    }

    /** Últimos registros, del más reciente al más antiguo; opcionalmente de un usuario. */
    public List<Entry> recent(String userEmail, int limit) {
        return jdbc.query("""
                select id, occurred_at, user_email, method, path, status, client_ip
                from audit_log
                where (cast(:user as varchar) is null or user_email = :user)
                order by occurred_at desc, id desc
                limit :limit
                """, new MapSqlParameterSource().addValue("user", userEmail).addValue("limit", limit),
                (rs, i) -> new Entry(rs.getLong("id"), rs.getObject("occurred_at", OffsetDateTime.class),
                        rs.getString("user_email"), rs.getString("method"), rs.getString("path"),
                        rs.getInt("status"), rs.getString("client_ip")));
    }
}
