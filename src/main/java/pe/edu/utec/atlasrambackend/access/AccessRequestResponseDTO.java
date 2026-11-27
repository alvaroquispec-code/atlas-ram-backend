package pe.edu.utec.atlasrambackend.access;

import pe.edu.utec.atlasrambackend.model.Role;

import java.time.Instant;
import java.util.List;

/**
 * Solicitud de acceso con lo que el sistema ya comprobó (checks) y el enlace al buscador del colegio
 * profesional para la última verificación.
 */
public record AccessRequestResponseDTO(
        Long id,
        String userEmail,
        String userFullName,
        Long facilityId,
        String facilityName,
        String facilityRenipressCode,
        Role requestedRole,
        ProfessionalBody professionalBody,
        String professionalBodyLabel,
        String registrationNumber,
        String lookupUrl,
        AccessRequestStatus status,
        Instant createdAt,
        Instant decidedAt,
        String note,
        List<String> checks
) {}
