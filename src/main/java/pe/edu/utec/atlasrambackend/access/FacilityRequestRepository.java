package pe.edu.utec.atlasrambackend.access;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FacilityRequestRepository extends JpaRepository<FacilityRequest, Long> {

    @EntityGraph(attributePaths = {"requester", "district"})
    List<FacilityRequest> findByStatusOrderByCreatedAtAsc(AccessRequestStatus status);

    @EntityGraph(attributePaths = {"district"})
    List<FacilityRequest> findByRequesterEmailOrderByCreatedAtDesc(String email);

    boolean existsByRenipressCodeAndStatus(String renipressCode, AccessRequestStatus status);
}
