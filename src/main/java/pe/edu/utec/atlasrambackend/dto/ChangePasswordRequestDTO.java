package pe.edu.utec.atlasrambackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cambio de contraseña por el propio usuario: exige la actual y aplica la misma política que el registro. */
public record ChangePasswordRequestDTO(
        @NotBlank String currentPassword,

        @NotBlank
        @Size(min = 8, max = 72)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "La contraseña debe incluir al menos una minúscula, una mayúscula y un número"
        )
        String newPassword
) {}
