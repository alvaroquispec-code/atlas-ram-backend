package pe.edu.utec.atlasrambackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.atlasrambackend.model.Facility;

import java.util.List;
import java.util.Optional;

public interface FacilityRepository extends JpaRepository<Facility, Long> {

    Optional<Facility> findByCode(String code);

    boolean existsByCode(String code);

    List<Facility> findByDistrictId(Long districtId);
}