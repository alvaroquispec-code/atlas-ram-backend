package pe.edu.utec.atlasrambackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.atlasrambackend.model.Microorganism;

import java.util.Optional;

public interface MicroorganismRepository extends JpaRepository<Microorganism, Long> {

    Optional<Microorganism> findByCode(String code);

    boolean existsByCode(String code);
}