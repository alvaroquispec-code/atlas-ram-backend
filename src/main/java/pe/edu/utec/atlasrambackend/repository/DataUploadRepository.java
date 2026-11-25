package pe.edu.utec.atlasrambackend.repository;

import pe.edu.utec.atlasrambackend.model.DataUpload;
import pe.edu.utec.atlasrambackend.model.UploadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DataUploadRepository extends JpaRepository<DataUpload, Long> {

    /** Carga vigente (no fallida ni revertida) del mismo archivo en el mismo establecimiento. */
    Optional<DataUpload> findFirstByFacilityIdAndFileSha256AndStatusIn(Long facilityId, String fileSha256,
                                                                       Collection<UploadStatus> statuses);

    @Query("select u.facility.id from DataUpload u where u.id = :id")
    Optional<Long> findFacilityIdById(@Param("id") Long id);

    Page<DataUpload> findByFacilityIdIn(Collection<Long> facilityIds, Pageable pageable);

    List<DataUpload> findByStatusAndFacilityIdIn(UploadStatus status, Collection<Long> facilityIds);

    List<DataUpload> findByFinishedAtIsNullAndFacilityIdIn(Collection<Long> facilityIds);

    Page<DataUpload> findByUploadedById(Long userId, Pageable pageable);

    List<DataUpload> findByStatus(UploadStatus status);

    List<DataUpload> findByFinishedAtIsNull();
}