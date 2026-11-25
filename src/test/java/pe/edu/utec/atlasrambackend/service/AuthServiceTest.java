package pe.edu.utec.atlasrambackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.utec.atlasrambackend.config.JwtService;
import pe.edu.utec.atlasrambackend.dto.AuthResponseDTO;
import pe.edu.utec.atlasrambackend.dto.ChangePasswordRequestDTO;
import pe.edu.utec.atlasrambackend.dto.RefreshRequestDTO;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.exception.InvalidTokenException;
import pe.edu.utec.atlasrambackend.model.Role;
import pe.edu.utec.atlasrambackend.model.User;
import pe.edu.utec.atlasrambackend.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final String EMAIL = "farmacia@atlasram.test";
    private static final String ACTUAL = "Actual2026x";

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final JwtService jwt = new JwtService("una-clave-de-prueba-de-al-menos-32-bytes!!", 60_000, 120_000);
    private final UserRepository users = mock(UserRepository.class);
    private final AuthService auth = new AuthService(users, encoder, jwt, mock(AuthenticationManager.class));
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail(EMAIL);
        user.setFullName("Farmacia de prueba");
        user.setRole(Role.PHARMACIST);
        user.setPasswordHash(encoder.encode(ACTUAL));
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    @Test
    void cambiarLaContrasenaGuardaElNuevoHashYRevocaLasSesionesAnteriores() {
        String refreshAnterior = jwt.generateRefreshToken(EMAIL, user.getTokenVersion());

        AuthResponseDTO nuevos = auth.changePassword(EMAIL, new ChangePasswordRequestDTO(ACTUAL, "Nueva2026x"));

        assertThat(encoder.matches("Nueva2026x", user.getPasswordHash())).isTrue();
        assertThat(user.getTokenVersion()).isEqualTo(2);
        assertThatThrownBy(() -> auth.refresh(new RefreshRequestDTO(refreshAnterior)))
                .isInstanceOf(InvalidTokenException.class);
        assertThat(auth.refresh(new RefreshRequestDTO(nuevos.refreshToken())).email()).isEqualTo(EMAIL);
    }

    @Test
    void sinLaContrasenaActualCorrectaNoSeCambiaNada() {
        String hashAntes = user.getPasswordHash();

        assertThatThrownBy(() -> auth.changePassword(EMAIL, new ChangePasswordRequestDTO("Otra2026x", "Nueva2026x")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("actual");
        assertThat(user.getPasswordHash()).isEqualTo(hashAntes);
        assertThat(user.getTokenVersion()).isEqualTo(1);
    }

    @Test
    void laNuevaContrasenaDebeSerDistintaDeLaActual() {
        assertThatThrownBy(() -> auth.changePassword(EMAIL, new ChangePasswordRequestDTO(ACTUAL, ACTUAL)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("distinta");
    }

    @Test
    void cerrarSesionEnTodosLosDispositivosInvalidaElRefreshToken() {
        String refresh = jwt.generateRefreshToken(EMAIL, user.getTokenVersion());

        auth.revokeAllSessions(EMAIL);

        assertThatThrownBy(() -> auth.refresh(new RefreshRequestDTO(refresh)))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void unUsuarioDesactivadoNoPuedeRefrescarSuSesion() {
        String refresh = jwt.generateRefreshToken(EMAIL, user.getTokenVersion());
        user.setActive(false);

        assertThatThrownBy(() -> auth.refresh(new RefreshRequestDTO(refresh)))
                .isInstanceOf(InvalidTokenException.class);
    }
}
