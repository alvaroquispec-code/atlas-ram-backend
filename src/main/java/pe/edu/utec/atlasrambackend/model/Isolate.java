package pe.edu.utec.atlasrambackend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Formula;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "isolate",
        indexes = {
                @Index(name = "idx_isolate_district_microorganism", columnList = "district_id, microorganism_id"),
                @Index(name = "idx_isolate_collection_date", columnList = "collection_date"),
                @Index(name = "idx_isolate_data_upload", columnList = "data_upload_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Isolate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "collection_date", nullable = false)
    private LocalDate collectionDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "specimen_type", nullable = false, length = 30)
    private Specimen specimenType;

    @Column(name = "patient_age")
    private Integer patientAge;

    @Column(name = "patient_sex", length = 1)
    private String patientSex;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id")
    private District district;

    @Setter(AccessLevel.NONE)
    @Formula("coalesce(district_id, (select f.district_id from facility f where f.id = facility_id))")
    private Long aggregationDistrictId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "microorganism_id", nullable = false)
    private Microorganism microorganism;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_upload_id")
    private DataUpload dataUpload;

    @OneToMany(
            mappedBy = "isolate",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<SusceptibilityResult> results = new ArrayList<>();

    public void addResult(SusceptibilityResult result) {
        results.add(result);
        result.setIsolate(this);
    }

    public void removeResult(SusceptibilityResult result) {
        results.remove(result);
        result.setIsolate(null);
    }
}