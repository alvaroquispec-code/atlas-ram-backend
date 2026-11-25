package pe.edu.utec.atlasrambackend.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pe.edu.utec.atlasrambackend.model.Role;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_VERSION = "ver";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long accessExpirationMs,
            @Value("${jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.key = Keys.hmacShaKeyFor(decodeSecret(secret));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    private static byte[] decodeSecret(String secret) {
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        try {
            byte[] decoded = Base64.getDecoder().decode(secret);
            if (decoded.length >= MIN_SECRET_BYTES) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
        }
        if (raw.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret debe tener al menos " + MIN_SECRET_BYTES
                            + " bytes. Generarlo con: openssl rand -base64 32");
        }
        return raw;
    }

    /** Sujeto de un token válido: el correo del usuario y la versión de sesión con que se emitió. */
    public record TokenSubject(String email, int version) {}

    public String generateAccessToken(String email, Role role, int sessionVersion) {
        return build(email, Map.of(CLAIM_TYPE, TYPE_ACCESS, CLAIM_ROLE, role.name(),
                CLAIM_VERSION, sessionVersion), accessExpirationMs);
    }

    public String generateRefreshToken(String email, int sessionVersion) {
        return build(email, Map.of(CLAIM_TYPE, TYPE_REFRESH, CLAIM_VERSION, sessionVersion),
                refreshExpirationMs);
    }

    private String build(String subject, Map<String, ?> claims, long expirationMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public Optional<TokenSubject> parseAccessToken(String token) {
        return subjectIfType(token, TYPE_ACCESS);
    }

    public Optional<TokenSubject> parseRefreshToken(String token) {
        return subjectIfType(token, TYPE_REFRESH);
    }

    /**
     * Devuelve el sujeto solo si la firma es válida, el token no venció, es del tipo esperado y trae la
     * versión de sesión. Un token sin "ver" (emitido antes de V12) se rechaza.
     */
    private Optional<TokenSubject> subjectIfType(String token, String expectedType) {
        try {
            Claims claims = parse(token);
            Integer version = claims.get(CLAIM_VERSION, Integer.class);
            if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))
                    || version == null || claims.getSubject() == null) {
                return Optional.empty();
            }
            return Optional.of(new TokenSubject(claims.getSubject(), version));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long getAccessExpirationSeconds() {
        return accessExpirationMs / 1000;
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}




