package pe.edu.utec.atlasrambackend.access;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, Long> {

    @EntityGraph(attributePaths = {"user", "facility"})
    List<AccessRequest> findByStatusOrderByCreatedAtAsc(AccessRequestStatus status);

    @EntityGraph(attributePaths = {"facility"})
    List<AccessRequest> findByUserEmailOrderByCreatedAtDesc(String email);

    boolean existsByUserIdAndStatus(Long userId, AccessRequestStatus status);
}
