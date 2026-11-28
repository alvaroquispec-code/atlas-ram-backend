package pe.edu.utec.atlasrambackend.access;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una fila del padrón RENIPRESS de SUSALUD (datos abiertos, licencia ODC-BY). Es la fuente oficial de si un
 * establecimiento es público o privado: se carga del CSV, no la escribe ningún usuario.
 */
@Entity
@Table(name = "renipress")
@Getter
@Setter
@NoArgsConstructor
public class Renipress {

    @Id
    @Column(length = 8)
    private String code;

    @Column(nullable = false, length = 300)
    private String name;

    /** Institución a la que pertenece según SUSALUD (MINSA, ESSALUD, GOBIERNO REGIONAL, PRIVADO...). */
    @Column(nullable = false, length = 120)
    private String institution;

    @Column(length = 20)
    private String category;

    @Column(length = 6)
    private String ubigeo;

    @Column(length = 40)
    private String status;

    @Column(name = "public_sector", nullable = false)
    private boolean publicSector;
}
