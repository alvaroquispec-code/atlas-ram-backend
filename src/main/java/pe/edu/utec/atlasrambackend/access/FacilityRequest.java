package pe.edu.utec.atlasrambackend.access;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.edu.utec.atlasrambackend.model.District;
import pe.edu.utec.atlasrambackend.model.Facility;
import pe.edu.utec.atlasrambackend.model.FacilitySector;
import pe.edu.utec.atlasrambackend.model.User;

import java.time.Instant;

/** Pedido de una clínica u hospital para unirse a Atlas RAM, con su RUC y su código IPRESS (V20). */
@Entity
@Table(name = "facility_request")
@Getter
@Setter
@NoArgsConstructor
public class FacilityRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id")
    private User requester;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 11)
    private String ruc;

    @Column(name = "renipress_code", nullable = false, length = 8)
    private String renipressCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "district_id")
    private District district;

    /** Lo que declaró quien pide. No decide nada: el sector lo dice RENIPRESS. */
    @Enumerated(EnumType.STRING)
    @Column(name = "claimed_sector", nullable = false, length = 10)
    private FacilitySector claimedSector;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccessRequestStatus status = AccessRequestStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "decided_at")
    private Instant decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(length = 300)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id")
    private Facility facility;
}
