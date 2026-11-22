package pe.edu.utec.atlasrambackend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(
        name="susceptibility_result",
        uniqueConstraints= @UniqueConstraint(
                name="uk_isolate_antibiotic",
                columnNames = {"isolate_id", "antibiotic_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class SusceptibilityResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch=FetchType.LAZY, optional = false)
    @JoinColumn(name="isolate_id",nullable = false)
    private Isolate isolate;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name="antibiotic_id", nullable = false)
    private Antibiotic antibiotic;

    @Positive
    @Column(name="mic_value", precision=10, scale=4)
    private BigDecimal micValue;

    /** Calificador de la CIM ("<=", ">="...). EQ o null significa un valor exacto. */
    @Enumerated(EnumType.STRING)
    @Column(name = "mic_qualifier", length = 2)
    private MicQualifier micQualifier;

    @Column(name="disk_diffusion_mm")
    private Integer diskDiffusionMm;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Interpretation interpretation;

    @Enumerated(EnumType.STRING)
    @Column(name = "reported_interpretation", length = 20)
    private Interpretation reportedInterpretation;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "interpretation_source", nullable = false, length = 20)
    private InterpretationSource interpretationSource = InterpretationSource.CALCULATED;

    @Column(name = "breakpoint_standard", length = 20)
    private String breakpointStandard;

    @Column(name = "breakpoint_version", length = 10)
    private String breakpointVersion;
}