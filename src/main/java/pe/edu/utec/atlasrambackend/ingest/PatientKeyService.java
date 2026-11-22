package pe.edu.utec.atlasrambackend.ingest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Seudonimiza el identificador de paciente con HMAC-SHA256. El sistema nunca guarda el identificador
 * original: solo esta clave, que sirve para reconocer al mismo paciente entre cargas (regla del primer
 * aislamiento de CLSI M39) sin saber quién es.
 *
 * Es seudonimización, no anonimización: quien tenga el secreto y el identificador original puede
 * recalcular la clave. Por eso el secreto vive fuera del código (variable PATIENT_KEY_SECRET) y la
 * clave incluye el establecimiento: la misma historia clínica en dos hospitales son dos personas
 * distintas, y así tampoco se pueden cruzar pacientes entre establecimientos.
 */
@Component
public class PatientKeyService {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKeySpec key;

    public PatientKeyService(@Value("${app.patient-key.secret}") String base64Secret) {
        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(base64Secret.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("PATIENT_KEY_SECRET debe estar en Base64", e);
        }
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("PATIENT_KEY_SECRET debe tener al menos 32 bytes (256 bits)");
        }
        this.key = new SecretKeySpec(secret, ALGORITHM);
    }

    /** Clave hexadecimal de 64 caracteres, o null si no hay identificador. */
    public String keyFor(String facilityCode, String rawPatientId) {
        String patientId = CsvValues.normalizePatientId(rawPatientId);
        if (patientId.isEmpty()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal((facilityCode + "|" + patientId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular la clave del paciente", e);
        }
    }
}
