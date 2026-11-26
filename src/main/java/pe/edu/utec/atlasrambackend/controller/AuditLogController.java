package pe.edu.utec.atlasrambackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utec.atlasrambackend.audit.AuditLog;

import java.util.List;

/** Consulta del registro de auditoría. Solo ADMIN (SecurityConfig). */
@RestController
@RequestMapping("/audit-log")
public class AuditLogController {

    private final AuditLog auditLog;

    public AuditLogController(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @GetMapping
    public ResponseEntity<List<AuditLog.Entry>> recent(@RequestParam(required = false) String user,
                                                       @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(auditLog.recent(user, Math.max(1, Math.min(limit, 500))));
    }
}
