package pe.edu.utec.atlasrambackend.service;

import pe.edu.utec.atlasrambackend.access.DataPolicy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.config.JwtService;
import pe.edu.utec.atlasrambackend.dto.AuthResponseDTO;
import pe.edu.utec.atlasrambackend.dto.ChangePasswordRequestDTO;
import pe.edu.utec.atlasrambackend.dto.LoginRequestDTO;
import pe.edu.utec.atlasrambackend.dto.RefreshRequestDTO;
import pe.edu.utec.atlasrambackend.dto.RegisterRequestDTO;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.exception.DuplicateResourceException;
import pe.edu.utec.atlasrambackend.exception.InvalidCredentialsException;
import pe.edu.utec.atlasrambackend.exception.InvalidTokenException;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.model.Role;
import pe.edu.utec.atlasrambackend.model.User;
import pe.edu.utec.atlasrambackend.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponseDTO register(RegisterRequestDTO dto, boolean requestedByAdmin) {
        if (!requestedByAdmin && !Boolean.TRUE.equals(dto.acceptedDataPolicy())) {
            throw new BusinessRuleException("Para crear la cuenta debes aceptar la política de tratamiento de datos personales");
        }
        if (userRepository.existsByEmail(dto.email())) {
            throw new DuplicateResourceException("un usuario", "el correo", dto.email());
        }
        User user = new User();
        user.setEmail(dto.email());
        user.setPasswordHash(passwordEncoder.encode(dto.password()));
        user.setFullName(dto.fullName());
        user.setRole(requestedByAdmin && dto.role() != null ? dto.role() : Role.PUBLIC_VIEWER);
        user.setActive(true);
        if (requestedByAdmin) {
            // Lo crea un administrador que conoce a la persona: no hace falta el enlace de verificación.
            user.setEmailVerifiedAt(java.time.Instant.now());
        }
        if (Boolean.TRUE.equals(dto.acceptedDataPolicy())) {
            user.setDataPolicyVersion(DataPolicy.VERSION);
            user.setDataPolicyAcceptedAt(java.time.Instant.now());
        }
        return tokensFor(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public AuthResponseDTO login(LoginRequestDTO dto) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.email(), dto.password()));
        User user = userRepository.findByEmail(dto.email())
                .orElseThrow(() -> new InvalidCredentialsException(
                        "Usuario autenticado sin registro: " + dto.email()));
        return tokensFor(user);
    }

    /** Rechaza el refresh token si venció, si el usuario está inactivo o si su sesión fue revocada. */
    @Transactional(readOnly = true)
    public AuthResponseDTO refresh(RefreshRequestDTO dto) {
        JwtService.TokenSubject subject = jwtService.parseRefreshToken(dto.refreshToken())
                .orElseThrow(() -> new InvalidTokenException("El refresh token es inválido o expiró"));
        User user = userRepository.findByEmail(subject.email())
                .filter(User::isActive)
                .filter(u -> u.getTokenVersion() == subject.version())
                .orElseThrow(() -> new InvalidTokenException("La sesión fue cerrada o el usuario está inactivo"));
        return tokensFor(user);
    }

    /**
     * Cambia la contraseña del usuario autenticado. Exige la contraseña actual (un token robado no basta
     * para tomar la cuenta), revoca todas las demás sesiones y devuelve tokens nuevos para la actual.
     */
    @Transactional
    public AuthResponseDTO changePassword(String email, ChangePasswordRequestDTO dto) {
        User user = getActiveOrThrow(email);
        if (!passwordEncoder.matches(dto.currentPassword(), user.getPasswordHash())) {
            // 422 y no 401: el cliente no debe interpretarlo como sesión vencida.
            throw new BusinessRuleException("La contraseña actual no es correcta");
        }
        if (passwordEncoder.matches(dto.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("La nueva contraseña debe ser distinta de la actual");
        }
        user.setPasswordHash(passwordEncoder.encode(dto.newPassword()));
        user.revokeSessions();
        return tokensFor(user);
    }

    /** "Cerrar sesión en todos los dispositivos": invalida todos los tokens del usuario, incluido el actual. */
    @Transactional
    public void revokeAllSessions(String email) {
        getActiveOrThrow(email).revokeSessions();
    }

    private User getActiveOrThrow(String email) {
        return userRepository.findByEmail(email)
                .filter(User::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", email));
    }

    private AuthResponseDTO tokensFor(User user) {
        return AuthResponseDTO.of(
                jwtService.generateAccessToken(user.getEmail(), user.getRole(), user.getTokenVersion()),
                jwtService.generateRefreshToken(user.getEmail(), user.getTokenVersion()),
                jwtService.getAccessExpirationSeconds(),
                user.getEmail(),
                user.getFullName(),
                user.getRole());
    }
}