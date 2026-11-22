package pe.edu.utec.atlasrambackend.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Huella de un archivo: dos archivos con los mismos bytes tienen la misma huella. */
public final class FileFingerprint {

    private FileFingerprint() {
    }

    /** SHA-256 en hexadecimal (64 caracteres). */
    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            // Toda JVM está obligada a incluir SHA-256.
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
