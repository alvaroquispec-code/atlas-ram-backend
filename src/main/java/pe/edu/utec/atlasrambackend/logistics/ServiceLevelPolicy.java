package pe.edu.utec.atlasrambackend.logistics;

import pe.edu.utec.atlasrambackend.model.AwareCategory;
import pe.edu.utec.atlasrambackend.model.VenClass;

import java.math.BigDecimal;

/**
 * Nivel de servicio por ciclo (probabilidad de no quedarse sin stock entre dos reposiciones) según la
 * criticidad: un antibiótico VITAL o de reserva AWaRe (último recurso) no puede faltar.
 * El establecimiento puede fijar otro valor por ítem.
 */
public final class ServiceLevelPolicy {

    public static final double CRITICAL = 0.99;
    public static final double IMPORTANT = 0.95;
    public static final double STANDARD = 0.90;

    private ServiceLevelPolicy() {
    }

    public static double target(VenClass ven, AwareCategory aware, BigDecimal override) {
        if (override != null) {
            return override.doubleValue();
        }
        if (ven == VenClass.VITAL || aware == AwareCategory.RESERVE) {
            return CRITICAL;
        }
        if (ven == VenClass.ESSENTIAL || aware == AwareCategory.WATCH) {
            return IMPORTANT;
        }
        return STANDARD;
    }
}
