package pe.edu.utec.atlasrambackend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Arrays;
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

    /** Código único de IPRESS asignado por SUSALUD en el RENIPRESS (8 dígitos). */
    @Column(name = "renipress_code", length = 8)
    private String renipressCode;

    /**
     * Dominios de correo institucional del establecimiento, separados por coma ("essalud.gob.pe"). Un
     * profesional con correo verificado de uno de estos dominios recibe el rol sin aprobación manual.
     */
    @Column(name = "email_domains", length = 300)
    private String emailDomains;

    /** Público o privado, según RENIPRESS (V20). Lo decide el padrón de SUSALUD, no quien se registra. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private FacilitySector sector;

    @Column(length = 11)
    private String ruc;

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

    /** ¿El dominio del correo pertenece a este establecimiento? */
    public boolean hasEmailDomain(String domain) {
        if (emailDomains == null || domain == null || domain.isBlank()) {
            return false;
        }
        return Arrays.stream(emailDomains.split(","))
                .map(d -> d.trim().toLowerCase())
                .anyMatch(d -> !d.isEmpty() && d.equals(domain.toLowerCase()));
    }
}
