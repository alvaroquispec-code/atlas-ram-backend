package pe.edu.utec.atlasrambackend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Señal estadística de un posible brote: en la ventana reciente, la proporción de aislamientos
 * resistentes de un microorganismo a una clase de antibiótico supera lo esperable según la línea
 * base del mismo establecimiento.
 */
@Entity
@Table(name = "resistance_alert")
@Getter
@Setter
@NoArgsConstructor
public class ResistanceAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "microorganism_id", nullable = false)
    private Microorganism microorganism;

    /** Antibiótico de la clase con la señal más fuerte; se muestra como referencia. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "antibiotic_id", nullable = false)
    private Antibiotic antibiotic;

    @NotNull
    @Column(name = "antibiotic_class", nullable = false, length = 100)
    private String antibioticClass;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertStatus status = AlertStatus.OPEN;

    /** Fecha de la evaluación que creó la alerta. No se modifica en las actualizaciones posteriores. */
    @NotNull
    @Column(name = "detected_on", nullable = false, updatable = false)
    private LocalDate detectedOn;

    @NotNull
    @Column(name = "window_start", nullable = false)
    private LocalDate windowStart;

    @NotNull
    @Column(name = "window_end", nullable = false)
    private LocalDate windowEnd;

    @Column(name = "tested_count", nullable = false)
    private int testedCount;

    @Column(name = "resistant_count", nullable = false)
    private int resistantCount;

    @Column(name = "baseline_tested", nullable = false)
    private int baselineTested;

    @Column(name = "baseline_resistant", nullable = false)
    private int baselineResistant;

    @Column(name = "p_value", nullable = false)
    private double pValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    /** Por qué se cerró, cuando no la cerró una persona (por ejemplo, al revertir la carga que la originó). */
    @Column(name = "close_reason", length = 500)
    private String closeReason;
}
