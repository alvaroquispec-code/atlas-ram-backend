package pe.edu.utec.atlasrambackend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un antibiótico en una presentación concreta (por ejemplo, meropenem vial 1 g) dentro de la farmacia
 * de un establecimiento. Guarda los parámetros de la política de reposición (R, S): cada cuánto se revisa
 * y pide (review_period_days) y cuánto tarda en llegar el pedido (lead_time_days).
 */
@Entity
@Table(name = "stock_item")
@Getter
@Setter
@NoArgsConstructor
public class StockItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "antibiotic_id", nullable = false)
    private Antibiotic antibiotic;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String presentation;

    @NotNull
    @Column(name = "grams_per_unit", nullable = false, precision = 10, scale = 4)
    private BigDecimal gramsPerUnit;

    /** DDD de la OMS para la vía de esta presentación. */
    @Column(name = "ddd_grams", precision = 10, scale = 4)
    private BigDecimal dddGrams;

    @Column(name = "unit_cost", precision = 12, scale = 2)
    private BigDecimal unitCost;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "ven_class", nullable = false, length = 20)
    private VenClass venClass;

    @Column(name = "review_period_days", nullable = false)
    private int reviewPeriodDays = 30;

    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays = 15;

    /** Nivel de servicio objetivo; nulo = se deriva de la criticidad. */
    @Column(name = "service_level", precision = 4, scale = 3)
    private BigDecimal serviceLevel;

    @Column(name = "quantity_on_order", nullable = false)
    private int quantityOnOrder;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
