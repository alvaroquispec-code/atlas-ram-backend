package pe.edu.utec.atlasrambackend.controller;

import pe.edu.utec.atlasrambackend.access.AccessRequestService;
import pe.edu.utec.atlasrambackend.access.EmailVerificationService;
import pe.edu.utec.atlasrambackend.repository.FacilityRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.AuthenticationException;
import pe.edu.utec.atlasrambackend.config.LoginAttemptLimiter;
import pe.edu.utec.atlasrambackend.exception.InvalidCredentialsException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utec.atlasrambackend.dto.AuthResponseDTO;
import pe.edu.utec.atlasrambackend.dto.ChangePasswordRequestDTO;
import pe.edu.utec.atlasrambackend.dto.FacilityResponseDTO;
import pe.edu.utec.atlasrambackend.dto.LoginRequestDTO;
import pe.edu.utec.atlasrambackend.dto.RefreshRequestDTO;
import pe.edu.utec.atlasrambackend.dto.RegisterRequestDTO;
import pe.edu.utec.atlasrambackend.dto.UserResponseDTO;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.mapper.UserMapper;
import pe.edu.utec.atlasrambackend.model.User;
import pe.edu.utec.atlasrambackend.repository.UserRepository;
import pe.edu.utec.atlasrambackend.service.AuthService;
import pe.edu.utec.atlasrambackend.service.FacilityService;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";
    // Mismos roles que FacilityAccessEvaluator deja registrar en cualquier establecimiento.
    private static final Set<String> GLOBAL_ACCESS_AUTHORITIES = Set.of("ROLE_ADMIN", "ROLE_EPIDEMIOLOGIST");

    private final AuthService authService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final FacilityService facilityService;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final AccessRequestService accessRequestService;
    private final EmailVerificationService emailVerificationService;
    private final FacilityRepository facilityRepository;

    public record VerifyEmailDTO(String token) {
    }

    public record VerificationResultDTO(boolean emailVerified, boolean sent, String message) {
    }

    public record ManagedFacilityDTO(Long id, String name) {
    }

    public AuthController(AuthService authService,
                          UserRepository userRepository,
                          UserMapper userMapper,
                          FacilityService facilityService,
                          LoginAttemptLimiter loginAttemptLimiter,
                          AccessRequestService accessRequestService,
                          EmailVerificationService emailVerificationService,
                          FacilityRepository facilityRepository) {
        this.emailVerificationService = emailVerificationService;
        this.facilityRepository = facilityRepository;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.accessRequestService = accessRequestService;
        this.authService = authService;
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.facilityService = facilityService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@Valid @RequestBody RegisterRequestDTO dto,
                                                    Authentication authentication) {
        boolean asAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
        boolean professional = dto.professional() != null && !asAdmin;
        if (professional) {
            accessRequestService.validate(dto.professional());
        }
        AuthResponseDTO response = authService.register(dto, asAdmin);
        if (professional) {
            accessRequestService.create(dto.email(), dto.professional());
        }
        if (!asAdmin) {
            userRepository.findByEmail(dto.email()).ifPresent(emailVerificationService::sendLink);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Enlace del correo de verificación (público: quien lo abre puede no haber iniciado sesión). */
    @PostMapping("/verify-email")
    public ResponseEntity<VerificationResultDTO> verifyEmail(@RequestBody VerifyEmailDTO dto) {
        User user = emailVerificationService.verify(dto == null ? null : dto.token());
        return ResponseEntity.ok(new VerificationResultDTO(true, false,
                "Correo confirmado: " + user.getEmail() + ". Si tu solicitud de acceso cumplía las condiciones, "
                        + "ya tienes el rol; vuelve a iniciar sesión para verlo."));
    }

    /** Reenvía el enlace de verificación a quien tiene la sesión. */
    @PostMapping("/me/verify-email")
    public ResponseEntity<VerificationResultDTO> resendVerification(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", authentication.getName()));
        if (user.isEmailVerified()) {
            return ResponseEntity.ok(new VerificationResultDTO(true, false, "Tu correo ya está confirmado."));
        }
        boolean sent = emailVerificationService.sendLink(user);
        return ResponseEntity.ok(new VerificationResultDTO(false, sent, sent
                ? "Te enviamos un enlace a " + user.getEmail() + " (vence en 24 horas)."
                : "No se envió: espera un minuto entre enlaces. Si sigue sin llegar, el envío de correos está en mantenimiento."));
    }

    /** Establecimientos donde quien consulta es jefe (puede aprobar a su personal). */
    @GetMapping("/me/managed-facilities")
    public ResponseEntity<List<ManagedFacilityDTO>> managedFacilities(Authentication authentication) {
        return ResponseEntity.ok(facilityRepository.findManagedBy(authentication.getName()).stream()
                .map(f -> new ManagedFacilityDTO(f.getId(), f.getName())).toList());
    }

    /** Con límite de intentos fallidos por correo y por IP (LoginAttemptLimiter): 429 al superarlo. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto, HttpServletRequest request) {
        String ip = LoginAttemptLimiter.clientIp(request);
        loginAttemptLimiter.checkAllowed(dto.email(), ip);
        try {
            AuthResponseDTO response = authService.login(dto);
            loginAttemptLimiter.recordSuccess(dto.email());
            return ResponseEntity.ok(response);
        } catch (AuthenticationException | InvalidCredentialsException e) {
            loginAttemptLimiter.recordFailure(dto.email(), ip);
            throw e;
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDTO> refresh(@Valid @RequestBody RefreshRequestDTO dto) {
        return ResponseEntity.ok(authService.refresh(dto));
    }

    /** El usuario autenticado cambia su contraseña; responde con tokens nuevos y cierra sus otras sesiones. */
    @PutMapping("/me/password")
    public ResponseEntity<AuthResponseDTO> changePassword(@Valid @RequestBody ChangePasswordRequestDTO dto,
                                                          Authentication authentication) {
        return ResponseEntity.ok(authService.changePassword(authentication.getName(), dto));
    }

    /** Cierra la sesión en todos los dispositivos, incluido este. */
    @PostMapping("/me/logout-all")
    public ResponseEntity<Void> logoutAll(Authentication authentication) {
        authService.revokeAllSessions(authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> me(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", authentication.getName()));
        return ResponseEntity.ok(userMapper.toResponse(user));
    }

    /** Establecimientos donde el usuario autenticado puede registrar aislamientos. */
    @GetMapping("/me/facilities")
    public ResponseEntity<List<FacilityResponseDTO>> myFacilities(Authentication authentication) {
        boolean globalAccess = authentication.getAuthorities().stream()
                .anyMatch(a -> GLOBAL_ACCESS_AUTHORITIES.contains(a.getAuthority()));
        return ResponseEntity.ok(facilityService.findAccessible(authentication.getName(), globalAccess));
    }
}
