package pe.edu.utec.atlasrambackend.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.config.JwtService;
import pe.edu.utec.atlasrambackend.dto.AuthResponseDTO;
import pe.edu.utec.atlasrambackend.dto.LoginRequestDTO;
import pe.edu.utec.atlasrambackend.dto.RefreshRequestDTO;
import pe.edu.utec.atlasrambackend.dto.RegisterRequestDTO;
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
        if (userRepository.existsByEmail(dto.email())) {
            throw new IllegalArgumentException("El correo ya está registrado: " + dto.email());
        }
        User user = new User();
        user.setEmail(dto.email());
        user.setPasswordHash(passwordEncoder.encode(dto.password()));
        user.setFullName(dto.fullName());
        user.setRole(requestedByAdmin && dto.role() != null ? dto.role() : Role.PUBLIC_VIEWER);
        user.setActive(true);
        return tokensFor(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public AuthResponseDTO login(LoginRequestDTO dto) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.email(), dto.password()));
        User user = userRepository.findByEmail(dto.email())
                .orElseThrow(() -> new IllegalStateException(
                        "Usuario autenticado sin registro: " + dto.email()));
        return tokensFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponseDTO refresh(RefreshRequestDTO dto) {
        String email = jwtService.extractEmailFromRefreshToken(dto.refreshToken());
        if (email == null) {
            throw new IllegalArgumentException("El refresh token es inválido o expiró");
        }
        User user = userRepository.findByEmail(email)
                .filter(User::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Usuario inactivo o inexistente"));
        return tokensFor(user);
    }

    private AuthResponseDTO tokensFor(User user) {
        return AuthResponseDTO.of(
                jwtService.generateAccessToken(user.getEmail(), user.getRole()),
                jwtService.generateRefreshToken(user.getEmail()),
                jwtService.getAccessExpirationSeconds(),
                user.getEmail(),
                user.getFullName(),
                user.getRole());
    }
}




