package pe.edu.utec.atlasrambackend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name="app_user")
@Getter
@Setter
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Email
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @NotBlank
    @Column(name="password_hash", nullable = false, length = 100)
    private String passwordHash;

    @NotBlank
    @Column(name="full_name", nullable = false, length = 150)
    private String fullName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_facility",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "facility_id")
    )
    private Set<Facility> facilities = new HashSet<>();

    @Column(nullable = false)
    private boolean active=true;

    @Column(name="created_at", nullable = false,updatable = false)
    private Instant createdAt= Instant.now();

    /**
     * Versión de sesión: viaja en cada JWT (claim "ver"). Al incrementarla, todos los tokens emitidos
     * antes dejan de valer. Ver V12__version_de_sesion.sql.
     */
    @Column(name="token_version", nullable = false)
    private int tokenVersion = 1;

    /** Versión de la política de datos personales que aceptó al registrarse (null en cuentas anteriores). */
    @Column(name="data_policy_version", length = 20)
    private String dataPolicyVersion;

    @Column(name="data_policy_accepted_at")
    private Instant dataPolicyAcceptedAt;

    /** Cuándo confirmó su correo con el enlace que le enviamos (V20). Null: aún no lo confirma. */
    @Column(name="email_verified_at")
    private Instant emailVerifiedAt;

    /** Establecimientos donde es administrador: aprueba las solicitudes de su personal (V20). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "facility_manager",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "facility_id")
    )
    private Set<Facility> managedFacilities = new HashSet<>();

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    /** Dominio del correo, en minúsculas ("essalud.gob.pe"). */
    public String emailDomain() {
        int at = email == null ? -1 : email.lastIndexOf('@');
        return at < 0 ? "" : email.substring(at + 1).toLowerCase();
    }

    /** Invalida todos los tokens emitidos hasta ahora. */
    public void revokeSessions() {
        tokenVersion++;
    }



}
