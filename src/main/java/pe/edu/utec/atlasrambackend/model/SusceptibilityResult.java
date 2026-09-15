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

    @Column(name="disk_diffusion_mm")
    private Integer diskDiffusionMm;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Interpretation interpretation;


}
