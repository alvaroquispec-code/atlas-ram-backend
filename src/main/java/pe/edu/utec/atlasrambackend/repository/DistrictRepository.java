package pe.edu.utec.atlasrambackend.repository;

import pe.edu.utec.atlasrambackend.model.District;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DistrictRepository extends JpaRepository<District, Long> {
    Optional<District> findByUbigeo(String ubigeo);

    boolean existsByUbigeo(String ubigeo);
}
