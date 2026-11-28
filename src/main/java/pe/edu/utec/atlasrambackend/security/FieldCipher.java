package pe.edu.utec.atlasrambackend.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifrado por columna de datos personales de profesionales (AES-256-GCM), además del cifrado de disco de
 * la base (RDS con KMS). Si alguien obtiene una copia de la base o un volcado, la colegiatura no se lee.
 *
 * Formato guardado: "v1:" + Base64(IV de 12 bytes || texto cifrado || etiqueta de 16 bytes). GCM además
 * autentica: si alguien altera el valor en la base, descifrar falla en vez de devolver basura.
 *
 * Clave: FIELD_ENCRYPTION_KEY (Base64, 32 bytes). Si no está, se deriva de PATIENT_KEY_SECRET con
 * HMAC-SHA256 y una etiqueta propia, para no reutilizar la misma clave en dos usos. Los valores antiguos
 * sin el prefijo "v1:" se leen tal cual (texto plano de antes de V20) y se cifran al volver a guardarse.
 */
@Component
public class FieldCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Instancia activa, para el conversor JPA (Hibernate lo crea fuera de Spring). */
    private static volatile FieldCipher active;

    private final SecretKeySpec key;

    public FieldCipher(@Value("${app.field-encryption.key:}") String base64Key,
                       @Value("${app.patient-key.secret}") String patientSecret) {
        this.key = new SecretKeySpec(resolveKey(base64Key, patientSecret), "AES");
    }

    /** Spring la registra al arrancar; las instancias creadas en pruebas unitarias no la reemplazan. */
    @PostConstruct
    void register() {
        active = this;
    }

    static byte[] resolveKey(String base64Key, String patientSecret) {
        if (base64Key != null && !base64Key.isBlank()) {
            byte[] raw = Base64.getDecoder().decode(base64Key.trim());
            if (raw.length != 32) {
                throw new IllegalStateException("FIELD_ENCRYPTION_KEY debe tener 32 bytes en Base64");
            }
            return raw;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(patientSecret.trim()), "HmacSHA256"));
            return mac.doFinal("atlasram-field-encryption-v1".getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("No se pudo derivar la clave de cifrado de campos", e);
        }
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el dato", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("No se pudo descifrar el dato (¿clave distinta o valor alterado?)", e);
        }
    }

    static FieldCipher active() {
        return active;
    }
}
