package pe.edu.utec.atlasrambackend.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FileFingerprintTest {

    @Test
    void coincideConLosVectoresDePruebaDelNist() {
        // FIPS 180-2, ejemplo "abc", y la huella del archivo vacío.
        assertThat(FileFingerprint.sha256("abc".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(FileFingerprint.sha256(new byte[0]))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void unSoloByteDistintoCambiaLaHuella() {
        String a = FileFingerprint.sha256("MEM,<=0.25".getBytes(StandardCharsets.UTF_8));
        String b = FileFingerprint.sha256("MEM,<=0.5".getBytes(StandardCharsets.UTF_8));
        assertThat(a).hasSize(64).isNotEqualTo(b);
    }
}
