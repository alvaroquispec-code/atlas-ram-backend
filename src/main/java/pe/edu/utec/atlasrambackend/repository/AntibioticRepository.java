package pe.edu.utec.atlasrambackend.repository;

import pe.edu.utec.atlasrambackend.model.Antibiotic;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AntibioticRepository extends JpaRepository<Antibiotic, Long> {
    Optional<Antibiotic> findByCode(String code);
}