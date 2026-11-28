package pe.edu.utec.atlasrambackend.access;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, Long> {

    @EntityGraph(attributePaths = {"user", "facility"})
    List<AccessRequest> findByStatusOrderByCreatedAtAsc(AccessRequestStatus status);

    @EntityGraph(attributePaths = {"user", "facility"})
    List<AccessRequest> findByStatusAndFacilityIdInOrderByCreatedAtAsc(AccessRequestStatus status,
                                                                       Collection<Long> facilityIds);

    @EntityGraph(attributePaths = {"facility"})
    List<AccessRequest> findByUserEmailOrderByCreatedAtDesc(String email);

    @EntityGraph(attributePaths = {"facility"})
    List<AccessRequest> findByUserIdAndStatus(Long userId, AccessRequestStatus status);

    boolean existsByUserIdAndStatus(Long userId, AccessRequestStatus status);
}
