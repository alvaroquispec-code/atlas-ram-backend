package pe.edu.utec.atlasrambackend.controller;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utec.atlasrambackend.config.FacilityAccessEvaluator;
import pe.edu.utec.atlasrambackend.dto.AlertResponseDTO;
import pe.edu.utec.atlasrambackend.dto.UpdateAlertStatusDTO;
import pe.edu.utec.atlasrambackend.model.AlertStatus;
import pe.edu.utec.atlasrambackend.service.AlertService;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/alerts")
public class AlertController {

    private final AlertService alertService;
    private final FacilityAccessEvaluator facilityAccess;

    public AlertController(AlertService alertService, FacilityAccessEvaluator facilityAccess) {
        this.alertService = alertService;
        this.facilityAccess = facilityAccess;
    }

    /** Un técnico de laboratorio solo ve las alertas de sus establecimientos. */
    @GetMapping
    public ResponseEntity<List<AlertResponseDTO>> findAll(@RequestParam(required = false) AlertStatus status,
                                                          Authentication authentication) {
        Set<Long> allowedFacilityIds = facilityAccess.visibleFacilityIds(authentication).orElse(null);
        return ResponseEntity.ok(alertService.findAll(status, allowedFacilityIds));
    }

    /** Reconocer o cerrar una alerta: decisión del comité de infecciones. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<AlertResponseDTO> updateStatus(@PathVariable Long id,
                                                         @Valid @RequestBody UpdateAlertStatusDTO dto) {
        return ResponseEntity.ok(alertService.updateStatus(id, dto.status()));
    }

    /**
     * Reevalúa un periodo pasado semana a semana y devuelve las alertas que se habrían generado.
     * Ejemplo: /alerts/replay?facilityId=6&from=2026-03-02&to=2026-06-29&stepDays=7
     */
    @PostMapping("/replay")
    @PreAuthorize("@facilityAccess.canAccess(authentication, #facilityId)")
    public ResponseEntity<List<AlertResponseDTO>> replay(
            @RequestParam Long facilityId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "7") int stepDays) {
        return ResponseEntity.ok(alertService.replay(facilityId, from, to, stepDays));
    }
}
