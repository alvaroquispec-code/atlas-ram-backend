package pe.edu.utec.atlasrambackend.ingest;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientKeyServiceTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);
    private final PatientKeyService service = new PatientKeyService(SECRET);

    @Test
    void mismoPacienteMismaClaveSinGuardarElIdentificador() {
        String a = service.keyFor("HNCH", "HC-0012345");
        String b = service.keyFor("HNCH", "hc 0012345");

        assertThat(a).isEqualTo(b).hasSize(64).doesNotContain("0012345");
    }

    @Test
    void laMismaHistoriaEnOtroHospitalEsOtraPersona() {
        assertThat(service.keyFor("HNCH", "12345")).isNotEqualTo(service.keyFor("HNHU", "12345"));
    }

    @Test
    void otroSecretoDaOtraClave() {
        // Ojo: 32 y 33 bytes en cero serían el MISMO secreto para HMAC, que rellena la clave con ceros
        // hasta 64 bytes (RFC 2104). Por eso el otro secreto se distingue en su contenido, no en su largo.
        byte[] different = new byte[32];
        Arrays.fill(different, (byte) 1);
        PatientKeyService other = new PatientKeyService(Base64.getEncoder().encodeToString(different));
        assertThat(other.keyFor("HNCH", "12345")).isNotEqualTo(service.keyFor("HNCH", "12345"));
    }

    @Test
    void sinIdentificadorNoHayClave() {
        assertThat(service.keyFor("HNCH", "  ")).isNull();
    }

    @Test
    void secretoDemasiadoCortoNoArranca() {
        String shortSecret = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new PatientKeyService(shortSecret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
