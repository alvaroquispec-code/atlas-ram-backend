package pe.edu.utec.atlasrambackend.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WardTest {

    @Test
    void seTraduceAlTipoDeLocalizacionDelIns() {
        assertThat(Ward.ICU.insLocationType()).isEqualTo("IN");
        assertThat(Ward.INPATIENT.insLocationType()).isEqualTo("IN");
        assertThat(Ward.OUTPATIENT.insLocationType()).isEqualTo("OUT");
        // Sin código en la tabla del INS: no se inventa.
        assertThat(Ward.EMERGENCY.insLocationType()).isNull();
    }
}
