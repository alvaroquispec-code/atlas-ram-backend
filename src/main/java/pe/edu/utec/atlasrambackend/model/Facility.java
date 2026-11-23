package pe.edu.utec.atlasrambackend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;


@Entity
@Table(name="facility")
@Getter
@Setter
@NoArgsConstructor
public class Facility {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false,unique = true,length = 20)
    private String code;

    @NotBlank
    @Column(nullable = false, length = 200)
    private String name;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "district_id", nullable = false)
    private District district;

    /** Camas de hospitalización; con la ocupación da los días-cama del indicador DDD. */
    @Column(name = "beds")
    private Integer beds;

    /** Ocupación promedio de camas, entre 0 y 1. */
    @Column(name = "occupancy_rate", precision = 4, scale = 3)
    private BigDecimal occupancyRate;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "facility_antibiotic_panel",
            joinColumns = @JoinColumn(name = "facility_id"),
            inverseJoinColumns = @JoinColumn(name = "antibiotic_id")
    )
    private Set<Antibiotic> antibioticPanel = new HashSet<>();
}
