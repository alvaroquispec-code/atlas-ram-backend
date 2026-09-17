package pe.edu.utec.atlasrambackend.dto;

import pe.edu.utec.atlasrambackend.model.Role;

public record AuthResponseDTO(
        String token,
        String email,
        String fullName,
        Role role
) {}

