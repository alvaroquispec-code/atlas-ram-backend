package pe.edu.utec.atlasrambackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Restablecimiento por el administrador (por ejemplo, un usuario que olvidó su contraseña). */
public record ResetPasswordRequestDTO(
        @NotBlank
        @Size(min = 8, max = 72)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "La contraseña debe incluir al menos una minúscula, una mayúscula y un número"
        )
        String newPassword
) {}
