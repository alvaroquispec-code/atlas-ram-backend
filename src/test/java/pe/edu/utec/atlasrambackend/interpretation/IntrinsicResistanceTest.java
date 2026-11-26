package pe.edu.utec.atlasrambackend.interpretation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntrinsicResistanceTest {

    @Test
    void reconoceLosParesDelApendiceB() {
        assertThat(IntrinsicResistance.isIntrinsic("eco", "VAN")).isTrue();
        assertThat(IntrinsicResistance.isIntrinsic("kpn", "AMP")).isTrue();
        assertThat(IntrinsicResistance.isIntrinsic("pae", "CRO")).isTrue();
        assertThat(IntrinsicResistance.isIntrinsic("efa", "CRO")).isTrue();
    }

    @Test
    void noMarcaParesQueSiSePrueban() {
        assertThat(IntrinsicResistance.isIntrinsic("eco", "AMP")).isFalse();
        assertThat(IntrinsicResistance.isIntrinsic("eco", "MEM")).isFalse();
        assertThat(IntrinsicResistance.isIntrinsic("sau", "VAN")).isFalse();
        assertThat(IntrinsicResistance.isIntrinsic(null, "VAN")).isFalse();
    }
}
