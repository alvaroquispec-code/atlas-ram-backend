package pe.edu.utec.atlasrambackend.repository;

import pe.edu.utec.atlasrambackend.model.Facility;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FacilityRepository extends JpaRepository<Facility, Long> {
    Optional<Facility> findByCode(String code);
    List<Facility> findByDistrictId(Long districtId);
}