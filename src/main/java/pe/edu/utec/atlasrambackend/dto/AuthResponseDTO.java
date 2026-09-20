package pe.edu.utec.atlasrambackend.dto;

import pe.edu.utec.atlasrambackend.model.Role;

public record AuthResponseDTO(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        String email,
        String fullName,
        Role role
) {
    public static AuthResponseDTO of(String accessToken, String refreshToken, long expiresInSeconds,
                                     String email, String fullName, Role role) {
        return new AuthResponseDTO(accessToken, refreshToken, "Bearer", expiresInSeconds,
                email, fullName, role);
    }
}


