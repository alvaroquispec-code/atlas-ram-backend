package pe.edu.utec.atlasrambackend.model;

/**
 * Clasificación AWaRe de la OMS (2023). Access: primera elección, menor potencial de resistencia.
 * Watch: mayor potencial de resistencia, uso vigilado. Reserve: último recurso para multirresistentes.
 */
public enum AwareCategory {
    ACCESS,
    WATCH,
    RESERVE,
    NOT_RECOMMENDED
}
