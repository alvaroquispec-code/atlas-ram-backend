package pe.edu.utec.atlasrambackend.repository;

import pe.edu.utec.atlasrambackend.model.SusceptibilityResult;
import pe.edu.utec.atlasrambackend.dto.AntibiogramResponseDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SusceptibilityResultRepository extends JpaRepository<SusceptibilityResult, Long> {

    List<SusceptibilityResult> findByIsolateId(Long isolateId);

    boolean existsByIsolateIdAndAntibioticId(Long isolateId, Long antibioticId);


    @Query("""
            select new pe.edu.utec.atlasrambackend.dto.AntibiogramResponseDTO(
                concat(m.genus, ' ', coalesce(m.species, '')),
                a.name,
                count(r),
                sum(case when r.interpretation = pe.edu.utec.atlasrambackend.model.Interpretation.RESISTANT then 1 else 0 end),
                (sum(case when r.interpretation = pe.edu.utec.atlasrambackend.model.Interpretation.RESISTANT then 1.0 else 0.0 end) * 100.0) / count(r)
            )
            from SusceptibilityResult r
            join r.isolate i
            join i.microorganism m
            join r.antibiotic a
            join i.facility f
            where (:microorganismId is null or m.id = :microorganismId)
              and (:districtId is null or f.district.id = :districtId)
              and i.collectionDate between :from and :to
            group by m.genus, m.species, a.name
            having count(r) >= 30
            order by a.name
            """)
    List<AntibiogramResponseDTO> calculateAntibiogram(
            @Param("microorganismId") Long microorganismId,
            @Param("districtId") Long districtId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}