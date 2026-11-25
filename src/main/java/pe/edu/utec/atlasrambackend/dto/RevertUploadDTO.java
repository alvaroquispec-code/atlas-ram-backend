package pe.edu.utec.atlasrambackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El motivo es obligatorio: queda en la auditoría de la carga. */
public record RevertUploadDTO(
        @NotBlank(message = "Indique por qué se revierte la carga")
        @Size(max = 500, message = "El motivo no puede superar 500 caracteres")
        String reason
) {}
