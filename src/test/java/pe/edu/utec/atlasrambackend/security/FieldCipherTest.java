package pe.edu.utec.atlasrambackend.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldCipherTest {

    private static final String PATIENT_SECRET =
            Base64.getEncoder().encodeToString("clave-de-pacientes-solo-para-pruebas-0001".getBytes());

    private final FieldCipher cipher = new FieldCipher("", PATIENT_SECRET);

    @Test
    void cifraYDescifraSinQueElTextoQuedeALaVista() {
        String stored = cipher.encrypt("12345");
        assertThat(stored).startsWith("v1:").doesNotContain("12345");
        assertThat(cipher.decrypt(stored)).isEqualTo("12345");
    }

    @Test
    void elMismoValorSeCifraDistintoCadaVez() {
        assertThat(cipher.encrypt("12345")).isNotEqualTo(cipher.encrypt("12345"));
    }

    @Test
    void leeTalCualLosValoresAntiguosSinCifrar() {
        assertThat(cipher.decrypt("54321")).isEqualTo("54321");
    }

    @Test
    void detectaSiAlguienAlteraElValorEnLaBase() {
        String stored = cipher.encrypt("12345");
        String tampered = stored.substring(0, stored.length() - 4) + (stored.endsWith("AAAA") ? "BBBB" : "AAAA");
        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void conOtraClaveNoSePuedeLeer() {
        String stored = cipher.encrypt("12345");
        String otherSecret = Base64.getEncoder().encodeToString("otra-clave-distinta-para-pruebas-000000002".getBytes());
        FieldCipher other = new FieldCipher("", otherSecret);
        assertThatThrownBy(() -> other.decrypt(stored)).isInstanceOf(IllegalStateException.class);
    }
}
