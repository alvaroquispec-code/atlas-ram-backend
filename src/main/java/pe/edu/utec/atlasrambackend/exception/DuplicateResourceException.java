package pe.edu.utec.atlasrambackend.exception;

public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }

    public DuplicateResourceException(String resource, String field, Object value) {
        super("Ya existe " + resource + " con " + field + ": " + value);
    }
}
