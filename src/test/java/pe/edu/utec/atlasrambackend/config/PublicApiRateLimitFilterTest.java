package pe.edu.utec.atlasrambackend.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PublicApiRateLimitFilterTest {

    private static UsernamePasswordAuthenticationToken user(String email, String role) {
        return new UsernamePasswordAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority(role)));
    }

    @Test
    void sinSesionSeCuentaPorIp() {
        assertThat(PublicApiRateLimitFilter.counterKey(null, "200.48.1.30")).isEqualTo("ip:200.48.1.30");
    }

    @Test
    void unaCuentaPublicaSeCuentaPorCuentaAunqueCambieDeIp() {
        var auth = user("Curioso@Mail.com", "ROLE_PUBLIC_VIEWER");
        assertThat(PublicApiRateLimitFilter.counterKey(auth, "1.1.1.1")).isEqualTo("user:curioso@mail.com");
        assertThat(PublicApiRateLimitFilter.counterKey(auth, "2.2.2.2")).isEqualTo("user:curioso@mail.com");
    }

    @Test
    void losProfesionalesNoSeLimitan() {
        assertThat(PublicApiRateLimitFilter.counterKey(user("lab@x.pe", "ROLE_LAB_TECHNICIAN"), "1.1.1.1")).isNull();
        assertThat(PublicApiRateLimitFilter.counterKey(user("admin@x.pe", "ROLE_ADMIN"), "1.1.1.1")).isNull();
    }

    @Test
    void lasPeticionesLocalesNoSeLimitan() {
        assertThat(PublicApiRateLimitFilter.counterKey(null, "127.0.0.1")).isNull();
    }
}
