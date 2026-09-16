package pe.edu.utec.atlasrambackend.repository;

import pe.edu.utec.atlasrambackend.model.Microorganism;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MicroorganismRepository extends JpaRepository<Microorganism, Long> {
    Optional<Microorganism> findByCode(String code);
}