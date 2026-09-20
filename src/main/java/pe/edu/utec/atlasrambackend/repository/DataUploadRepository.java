package pe.edu.utec.atlasrambackend.repository;

import pe.edu.utec.atlasrambackend.model.DataUpload;
import pe.edu.utec.atlasrambackend.model.UploadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataUploadRepository extends JpaRepository<DataUpload, Long> {

    Page<DataUpload> findByUploadedById(Long userId, Pageable pageable);

    List<DataUpload> findByStatus(UploadStatus status);

    List<DataUpload> findByFinishedAtIsNull();
}