package pe.edu.utec.atlasrambackend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(
        name = "breakpoint",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_breakpoint_combo",
                columnNames = {"microorganism_id", "antibiotic_id", "standard", "version"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class Breakpoint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name="microorganism_id", nullable=false)
    private Microorganism microorganism;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name="antibiotic_id", nullable = false)
    private Antibiotic antibiotic;

    @NotBlank
    @Column(nullable = false, length = 20)
    private String standard;

    @NotBlank
    @Column(nullable = false, length = 10)
    private String version;

    @Positive
    @Column(name="susceptible_max", precision=10, scale=4)
    private BigDecimal susceptibleMax;

    @Positive
    @Column(name="resistant_min", precision = 10, scale=4)
    private BigDecimal resistantMin;
}
