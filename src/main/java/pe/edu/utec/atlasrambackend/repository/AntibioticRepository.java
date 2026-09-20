package pe.edu.utec.atlasrambackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.atlasrambackend.model.Antibiotic;

import java.util.Optional;

public interface AntibioticRepository extends JpaRepository<Antibiotic, Long> {

    Optional<Antibiotic> findByCode(String code);

    boolean existsByCode(String code);
}