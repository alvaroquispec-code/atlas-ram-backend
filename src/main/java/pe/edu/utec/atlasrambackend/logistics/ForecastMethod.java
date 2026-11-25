package pe.edu.utec.atlasrambackend.logistics;

/** Método de pronóstico elegido para un ítem según su patrón de demanda. */
public enum ForecastMethod {
    /** Suavizamiento exponencial simple: demanda frecuente (SMOOTH, ERRATIC). */
    SES,
    /** Syntetos-Boylan Approximation: Croston con corrección de sesgo, para INTERMITTENT y LUMPY. */
    SBA,
    /** Teunter-Syntetos-Babai: intermitente con demanda que se está apagando (3+ meses finales en cero). */
    TSB,
    /** Sin historia suficiente o sin consumo en 6 meses: solo se informan los indicadores SISMED. */
    NONE
}
