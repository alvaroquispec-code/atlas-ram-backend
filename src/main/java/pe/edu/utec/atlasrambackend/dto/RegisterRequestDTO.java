package pe.edu.utec.atlasrambackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.edu.utec.atlasrambackend.model.Role;

public record RegisterRequestDTO(
        @NotBlank @Email @Size(max = 150) String email,

        @NotBlank
        @Size(min = 8, max = 72)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "La contraseña debe incluir al menos una minúscula, una mayúscula y un número"
        )
        String password,

        @NotBlank @Size(max = 150) String fullName,

        Role role,

        /** Aceptación de la política de datos personales; obligatoria cuando la persona se registra sola. */
        Boolean acceptedDataPolicy,

        /** Solo si es profesional de la salud: crea una solicitud de acceso con su colegiatura. */
        @jakarta.validation.Valid
        pe.edu.utec.atlasrambackend.access.ProfessionalRequestDTO professional
) {}


