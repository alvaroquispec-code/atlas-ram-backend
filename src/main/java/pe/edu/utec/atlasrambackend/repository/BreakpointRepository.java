package pe.edu.utec.atlasrambackend.repository;

import pe.edu.utec.atlasrambackend.model.Breakpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BreakpointRepository extends JpaRepository<Breakpoint, Long> {

    Optional<Breakpoint> findByMicroorganismIdAndAntibioticIdAndStandardAndVersion(
            Long microorganismId, Long antibioticId, String standard, String version);
}