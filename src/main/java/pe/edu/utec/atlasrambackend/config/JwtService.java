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

@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";
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

    public String generateAccessToken(String email, Role role) {
        return build(email, Map.of(CLAIM_TYPE, TYPE_ACCESS, CLAIM_ROLE, role.name()), accessExpirationMs);
    }

    public String generateRefreshToken(String email) {
        return build(email, Map.of(CLAIM_TYPE, TYPE_REFRESH), refreshExpirationMs);
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

    public String extractEmailFromAccessToken(String token) {
        return subjectIfType(token, TYPE_ACCESS);
    }

    public String extractEmailFromRefreshToken(String token) {
        return subjectIfType(token, TYPE_REFRESH);
    }

    private String subjectIfType(String token, String expectedType) {
        try {
            Claims claims = parse(token);
            return expectedType.equals(claims.get(CLAIM_TYPE, String.class)) ? claims.getSubject() : null;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
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




