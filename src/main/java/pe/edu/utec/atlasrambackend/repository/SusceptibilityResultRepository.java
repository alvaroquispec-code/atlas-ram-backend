package pe.edu.utec.atlasrambackend.repository;

import pe.edu.utec.atlasrambackend.model.SusceptibilityResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SusceptibilityResultRepository extends JpaRepository<SusceptibilityResult, Long> {

    List<SusceptibilityResult> findByIsolateId(Long isolateId);

    boolean existsByIsolateIdAndAntibioticId(Long isolateId, Long antibioticId);
}