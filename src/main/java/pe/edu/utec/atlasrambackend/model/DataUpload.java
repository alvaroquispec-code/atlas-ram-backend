package pe.edu.utec.atlasrambackend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name="data_upload")
@Getter
@Setter
@NoArgsConstructor
public class DataUpload {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name="file_name", nullable = false, length = 255)
    private String fileName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UploadStatus status= UploadStatus.PENDING;

    @Column(name="total_rows")
    private Integer totalRows;

    @Column(name="processed_rows")
    private Integer processedRows;

    @Column(name="failed_rows")
    private Integer failedRows;

    @Column(name="error_log", columnDefinition = "TEXT")
    private String errorLog;

    @Column(name="started_at", nullable = false, updatable = false)
    private Instant startedAt=Instant.now();

    @Column(name="finished_at")
    private Instant finishedAt;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name="facility_id", nullable = false)
    private Facility facility;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name= "uploaded_by", nullable = false)
    private User uploadedBy;
}
