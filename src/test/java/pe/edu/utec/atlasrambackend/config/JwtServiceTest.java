package pe.edu.utec.atlasrambackend.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import pe.edu.utec.atlasrambackend.model.Role;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "una-clave-de-prueba-de-al-menos-32-bytes!!";
    private final JwtService jwt = new JwtService(SECRET, 60_000, 120_000);

    @Test
    void elTokenDeAccesoLlevaCorreoYVersionDeSesion() {
        String token = jwt.generateAccessToken("lab@atlasram.test", Role.LAB_TECHNICIAN, 3);

        assertThat(jwt.parseAccessToken(token))
                .contains(new JwtService.TokenSubject("lab@atlasram.test", 3));
    }

    @Test
    void unTokenDeRefrescoNoSirveComoTokenDeAccesoNiAlReves() {
        String refresh = jwt.generateRefreshToken("lab@atlasram.test", 1);
        String access = jwt.generateAccessToken("lab@atlasram.test", Role.ADMIN, 1);

        assertThat(jwt.parseAccessToken(refresh)).isEmpty();
        assertThat(jwt.parseRefreshToken(access)).isEmpty();
        assertThat(jwt.parseRefreshToken(refresh)).isPresent();
    }

    @Test
    void seRechazaUnTokenEmitidoAntesDeV12SinVersionDeSesion() {
        Date now = new Date();
        String legacy = Jwts.builder()
                .subject("admin@atlasram.test")
                .claim("type", "access")
                .claim("role", "ADMIN")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(jwt.parseAccessToken(legacy)).isEmpty();
    }

    @Test
    void seRechazaUnTokenFirmadoConOtraClave() {
        JwtService otro = new JwtService("otra-clave-distinta-de-al-menos-32-bytes!!", 60_000, 120_000);
        String ajeno = otro.generateAccessToken("admin@atlasram.test", Role.ADMIN, 1);

        assertThat(jwt.parseAccessToken(ajeno)).isEmpty();
    }
}
