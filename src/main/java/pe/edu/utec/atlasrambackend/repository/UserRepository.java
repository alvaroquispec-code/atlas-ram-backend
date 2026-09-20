package pe.edu.utec.atlasrambackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utec.atlasrambackend.model.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("""
            select count(f) > 0 from User u
            join u.facilities f
            where u.email = :email and f.id = :facilityId
            """)
    boolean isAssignedToFacility(@Param("email") String email, @Param("facilityId") Long facilityId);
}