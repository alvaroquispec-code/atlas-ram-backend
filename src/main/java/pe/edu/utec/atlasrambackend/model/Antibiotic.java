package pe.edu.utec.atlasrambackend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name="antibiotic")
@Getter
@Setter
@NoArgsConstructor
public class Antibiotic {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @NotBlank
    @Column(nullable = false ,length = 100)
    private String name;

    @Column(length = 100)
    private String antibioticClass;


}
