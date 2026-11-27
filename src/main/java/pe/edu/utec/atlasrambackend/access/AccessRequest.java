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
import pe.edu.utec.atlasrambackend.model.Facility;
import pe.edu.utec.atlasrambackend.model.Role;
import pe.edu.utec.atlasrambackend.model.User;

import java.time.Instant;

/** Pedido de un profesional para recibir un rol clínico en un establecimiento (V18). */
@Entity
@Table(name = "access_request")
@Getter
@Setter
@NoArgsConstructor
public class AccessRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_id")
    private Facility facility;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_role", nullable = false, length = 30)
    private Role requestedRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "professional_body", nullable = false, length = 10)
    private ProfessionalBody professionalBody;

    @Column(name = "registration_number", nullable = false, length = 10)
    private String registrationNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
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
}
