package pe.edu.utec.atlasrambackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.atlasrambackend.model.Breakpoint;
import pe.edu.utec.atlasrambackend.model.BreakpointSite;

import java.util.List;

public interface BreakpointRepository extends JpaRepository<Breakpoint, Long> {

    /** Puede haber más de uno: uno por sitio de infección (ver BreakpointSite). */
    List<Breakpoint> findAllByMicroorganismIdAndAntibioticIdAndStandardAndVersion(
            Long microorganismId, Long antibioticId, String standard, String version);

    List<Breakpoint> findByStandardAndVersion(String standard, String version);

    /** Ediciones cargadas: estándar, versión y cuántos pares germen–antibiótico tiene cada una. */
    @org.springframework.data.jpa.repository.Query(
            "select b.standard, b.version, count(b) from Breakpoint b group by b.standard, b.version "
                    + "order by b.standard, b.version desc")
    List<Object[]> countByEdition();

    boolean existsByMicroorganismIdAndAntibioticIdAndStandardAndVersionAndSite(
            Long microorganismId, Long antibioticId, String standard, String version, BreakpointSite site);
}