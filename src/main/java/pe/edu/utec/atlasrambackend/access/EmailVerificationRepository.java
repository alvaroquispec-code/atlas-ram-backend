package pe.edu.utec.atlasrambackend.access;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, String> {

    @EntityGraph(attributePaths = {"user"})
    Optional<EmailVerification> findByTokenHash(String tokenHash);

    /** Para no reenviar más de un enlace por minuto (evita usar el servidor para mandar spam). */
    boolean existsByUserIdAndExpiresAtAfter(Long userId, java.time.Instant after);
}
