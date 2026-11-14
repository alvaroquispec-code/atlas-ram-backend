package pe.edu.utec.atlasrambackend.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utec.atlasrambackend.dto.AuthResponseDTO;
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

    public AuthController(AuthService authService,
                          UserRepository userRepository,
                          UserMapper userMapper,
                          FacilityService facilityService) {
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
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(dto, asAdmin));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto) {
        return ResponseEntity.ok(authService.login(dto));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDTO> refresh(@Valid @RequestBody RefreshRequestDTO dto) {
        return ResponseEntity.ok(authService.refresh(dto));
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
