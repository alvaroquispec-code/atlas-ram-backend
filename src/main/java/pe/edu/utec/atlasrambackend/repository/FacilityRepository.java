package pe.edu.utec.atlasrambackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utec.atlasrambackend.model.Facility;

import java.util.List;
import java.util.Optional;

public interface FacilityRepository extends JpaRepository<Facility, Long> {

    Optional<Facility> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByRenipressCode(String renipressCode);

    List<Facility> findByDistrictId(Long districtId);

    @Query("""
            select f from User u
            join u.facilities f
            where u.email = :email
            order by f.name
            """)
    List<Facility> findAssignedTo(@Param("email") String email);
}