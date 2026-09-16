package pe.edu.utec.atlasrambackend.repository;

import pe.edu.utec.atlasrambackend.model.Isolate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface IsolateRepository extends JpaRepository<Isolate, Long> {

    Page<Isolate> findByFacilityId(Long facilityId, Pageable pageable);

    List<Isolate> findByCollectionDateBetween(LocalDate from, LocalDate to);

    long countByDataUploadId(Long dataUploadId);

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
}