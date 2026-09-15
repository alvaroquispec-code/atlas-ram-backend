package pe.edu.utec.atlasrambackend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

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

    @Column(nullable = false)
    private boolean active=true;

    @Column(name="created_at", nullable = false,updatable = false)
    private Instant createdAt= Instant.now();



}
