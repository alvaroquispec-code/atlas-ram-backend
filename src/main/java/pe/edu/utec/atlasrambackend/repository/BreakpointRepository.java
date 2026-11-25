package pe.edu.utec.atlasrambackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.atlasrambackend.model.Breakpoint;

import java.util.List;
import java.util.Optional;

public interface BreakpointRepository extends JpaRepository<Breakpoint, Long> {

    Optional<Breakpoint> findByMicroorganismIdAndAntibioticIdAndStandardAndVersion(
            Long microorganismId, Long antibioticId, String standard, String version);

    List<Breakpoint> findByStandardAndVersion(String standard, String version);

    /** Ediciones cargadas: estándar, versión y cuántos pares germen–antibiótico tiene cada una. */
    @org.springframework.data.jpa.repository.Query(
            "select b.standard, b.version, count(b) from Breakpoint b group by b.standard, b.version "
                    + "order by b.standard, b.version desc")
    List<Object[]> countByEdition();

    boolean existsByMicroorganismIdAndAntibioticIdAndStandardAndVersion(
            Long microorganismId, Long antibioticId, String standard, String version);
}