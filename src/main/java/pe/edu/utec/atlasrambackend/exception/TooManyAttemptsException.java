package pe.edu.utec.atlasrambackend.exception;

/** Demasiados intentos fallidos de inicio de sesión: se responde 429 con Retry-After. */
public class TooManyAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyAttemptsException(long retryAfterSeconds) {
        super("Demasiados intentos fallidos. Intente de nuevo en " + Math.max(1, (retryAfterSeconds + 59) / 60)
                + " min.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
