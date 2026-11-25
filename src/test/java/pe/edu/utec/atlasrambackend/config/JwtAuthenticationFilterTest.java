package pe.edu.utec.atlasrambackend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.utec.atlasrambackend.model.Role;
import pe.edu.utec.atlasrambackend.model.User;
import pe.edu.utec.atlasrambackend.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private static final String EMAIL = "epi@atlasram.test";

    private final JwtService jwt = new JwtService("una-clave-de-prueba-de-al-menos-32-bytes!!", 60_000, 120_000);
    private final UserRepository users = mock(UserRepository.class);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwt, users, new SecurityExceptionHandler(new ObjectMapper()));
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail(EMAIL);
        user.setFullName("Epidemiología");
        user.setRole(Role.EPIDEMIOLOGIST);
        user.setPasswordHash("x");
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sinTokenLaPeticionSigueComoAnonima() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(null, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void conTokenVigenteSeAutenticaConElRolDeLaBase() throws Exception {
        String token = jwt.generateAccessToken(EMAIL, Role.EPIDEMIOLOGIST, user.getTokenVersion());
        MockFilterChain chain = new MockFilterChain();
        run(token, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo(EMAIL);
    }

    @Test
    void unTokenDeUnaSesionRevocadaRecibe401AunqueElEndpointSeaPublico() throws Exception {
        String token = jwt.generateAccessToken(EMAIL, Role.EPIDEMIOLOGIST, user.getTokenVersion());
        user.revokeSessions();
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(token, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).as("no debe llegar al controlador").isNull();
    }

    @Test
    void unUsuarioDesactivadoRecibe401() throws Exception {
        String token = jwt.generateAccessToken(EMAIL, Role.EPIDEMIOLOGIST, user.getTokenVersion());
        user.setActive(false);

        assertThat(run(token, new MockFilterChain()).getStatus()).isEqualTo(401);
    }

    @Test
    void unTokenMalFormadoRecibe401() throws Exception {
        assertThat(run("no-es-un-jwt", new MockFilterChain()).getStatus()).isEqualTo(401);
    }

    private MockHttpServletResponse run(String token, MockFilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/antibiogram/wisca");
        request.setServletPath("/antibiogram/wisca");
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
