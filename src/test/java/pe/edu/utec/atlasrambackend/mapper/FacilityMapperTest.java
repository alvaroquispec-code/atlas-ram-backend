package pe.edu.utec.atlasrambackend.mapper;

import org.junit.jupiter.api.Test;
import pe.edu.utec.atlasrambackend.dto.CreateFacilityDTO;
import pe.edu.utec.atlasrambackend.model.District;
import pe.edu.utec.atlasrambackend.model.Facility;

import static org.assertj.core.api.Assertions.assertThat;

class FacilityMapperTest {

    @Test
    void unCodigoRenipressVacioSeGuardaComoNulo() {
        District district = new District();
        district.setId(1L);
        district.setName("Lima");

        Facility blank = new FacilityMapper().toEntity(
                new CreateFacilityDTO("HX", "Hospital X", 1L, "  ", null, null), district);
        Facility filled = new FacilityMapper().toEntity(
                new CreateFacilityDTO("HY", "Hospital Y", 1L, " 00012345 ", null, null), district);

        assertThat(blank.getRenipressCode()).isNull();
        assertThat(filled.getRenipressCode()).isEqualTo("00012345");
    }
}
