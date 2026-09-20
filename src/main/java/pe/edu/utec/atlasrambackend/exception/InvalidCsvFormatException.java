package pe.edu.utec.atlasrambackend.exception;

public class InvalidCsvFormatException extends RuntimeException {
    public InvalidCsvFormatException(String message) {
        super(message);
    }
}
