package pe.edu.utec.atlasrambackend.repository;

import pe.edu.utec.atlasrambackend.model.SusceptibilityResult;
import pe.edu.utec.atlasrambackend.dto.ResistanceCountRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SusceptibilityResultRepository extends JpaRepository<SusceptibilityResult, Long> {

    /** Ordenados por antibiótico: sin ORDER BY, PostgreSQL los devuelve en cualquier orden. */
    List<SusceptibilityResult> findByIsolateIdOrderByAntibioticNameAsc(Long isolateId);

    /** Borrado masivo de los resultados de una carga (se ejecuta antes que el de sus aislamientos). */
    @Modifying(flushAutomatically = true)
    @Query("delete from SusceptibilityResult r where r.isolate.id in "
            + "(select i.id from Isolate i where i.dataUpload.id = :uploadId)")
    int deleteByUploadId(@Param("uploadId") Long uploadId);

    boolean existsByIsolateIdAndAntibioticId(Long isolateId, Long antibioticId);




    @Query("""
            select count(r) from SusceptibilityResult r
            where r.reportedInterpretation is not null
              and r.reportedInterpretation <> r.interpretation
            """)
    long countDiscordantWithReported();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from SusceptibilityResult r
            where r.isolate.id in (select i.id from Isolate i where i.dataUpload.id = :uploadId)
            """)
    int deleteByDataUploadId(@Param("uploadId") Long uploadId);

    /** Probados y resistentes por combinación en un establecimiento y periodo; insumo del detector de brotes. */
    @Query("""
            select new pe.edu.utec.atlasrambackend.dto.ResistanceCountRow(
                i.microorganism.id,
                r.antibiotic.id,
                count(r),
                sum(case when r.interpretation = pe.edu.utec.atlasrambackend.model.Interpretation.RESISTANT then 1 else 0 end)
            )
            from SusceptibilityResult r
            join r.isolate i
            where i.facility.id = :facilityId
              and i.collectionDate between :from and :to
            group by i.microorganism.id, r.antibiotic.id
            """)
    List<ResistanceCountRow> countByCombination(@Param("facilityId") Long facilityId,
                                                @Param("from") LocalDate from,
                                                @Param("to") LocalDate to);
}
