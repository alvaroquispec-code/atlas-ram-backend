package pe.edu.utec.atlasrambackend.dto;

import pe.edu.utec.atlasrambackend.model.Role;

import java.time.Instant;

public record UserResponseDTO(
        Long id,
        String email,
        String fullName,
        Role role,
        boolean active,
        Instant createdAt
) {}