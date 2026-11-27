package pe.edu.utec.atlasrambackend.access;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import pe.edu.utec.atlasrambackend.model.Role;

/** Datos extra del registro de un profesional de la salud. */
public record ProfessionalRequestDTO(
        @NotNull(message = "Elige tu establecimiento") Long facilityId,
        @NotNull(message = "Elige el rol que necesitas") Role requestedRole,
        @NotNull(message = "Elige tu colegio profesional") ProfessionalBody professionalBody,
        @NotNull(message = "Ingresa tu número de colegiatura")
        @Pattern(regexp = "^[0-9]{4,6}$", message = "La colegiatura tiene de 4 a 6 dígitos")
        String registrationNumber
) {}
