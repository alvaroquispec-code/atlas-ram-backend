package pe.edu.utec.atlasrambackend.repository;

import pe.edu.utec.atlasrambackend.dto.ReferralFacilityRow;
import pe.edu.utec.atlasrambackend.model.Isolate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface IsolateRepository extends JpaRepository<Isolate, Long>, JpaSpecificationExecutor<Isolate> {

    Page<Isolate> findByFacilityId(Long facilityId, Pageable pageable);

    /** Establecimiento de un aislamiento, sin cargar la entidad completa. */
    @Query("select i.facility.id from Isolate i where i.id = :id")
    Optional<Long> findFacilityIdById(@Param("id") Long id);

    List<Isolate> findByCollectionDateBetween(LocalDate from, LocalDate to);

    long countByDataUploadId(Long dataUploadId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Isolate i where i.dataUpload.id = :uploadId")
    int deleteByUploadId(@Param("uploadId") Long uploadId);

    /** Establecimientos donde se atendieron los pacientes de un distrito, del que más recibe al que menos. */
    @Query("""
        select new pe.edu.utec.atlasrambackend.dto.ReferralFacilityRow(f.id, f.name, count(i))
        from Isolate i
        join i.facility f
        where i.aggregationDistrictId = :districtId
          and i.collectionDate between :from and :to
        group by f.id, f.name
        order by count(i) desc
        """)
    List<ReferralFacilityRow> countByFacilityForDistrict(@Param("districtId") Long districtId,
                                                         @Param("from") LocalDate from,
                                                         @Param("to") LocalDate to);

    @Query("""
        select i from Isolate i
        join fetch i.facility
        left join fetch i.district
        join fetch i.microorganism
        left join fetch i.dataUpload
        where i.id = :id
        """)
    Isolate findByIdWithRelations(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Isolate i where i.dataUpload.id = :uploadId")
    int deleteByDataUploadId(@Param("uploadId") Long uploadId);

    /** Fecha de toma más reciente del establecimiento: el "hoy" de sus datos para evaluar alertas. */
    @Query("select max(i.collectionDate) from Isolate i where i.facility.id = :facilityId")
    Optional<LocalDate> findLatestCollectionDate(@Param("facilityId") Long facilityId);
}
