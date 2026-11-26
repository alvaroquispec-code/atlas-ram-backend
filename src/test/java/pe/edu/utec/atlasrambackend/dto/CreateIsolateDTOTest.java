package pe.edu.utec.atlasrambackend.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import pe.edu.utec.atlasrambackend.model.Specimen;
import pe.edu.utec.atlasrambackend.model.Ward;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CreateIsolateDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static CreateIsolateDTO dto(Integer age, String sex, Ward ward) {
        return new CreateIsolateDTO(LocalDate.of(2026, 11, 20), Specimen.BLOOD, age, sex, 1L, null, 1L, null, ward,
                List.of(new CreateIsolateResultDTO(11L, ">=16", null, null)), null, null);
    }

    @Test
    void conSexoEdadYServicioEsValido() {
        assertThat(validator.validate(dto(47, "F", Ward.ICU))).isEmpty();
    }

    @Test
    void sexoEdadYServicioSonObligatoriosComoPideElIns() {
        var errores = validator.validate(dto(null, null, null));

        assertThat(errores).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("patientAge", "patientSex", "ward");
    }

    @Test
    void elSexoSoloAdmiteMoF() {
        assertThat(validator.validate(dto(47, "X", Ward.ICU))).extracting(v -> v.getMessage())
                .containsExactly("El sexo debe ser M o F");
    }

    @Test
    void sinAntibiogramaNoSeRegistra() {
        var sinResultados = new CreateIsolateDTO(LocalDate.of(2026, 11, 20), Specimen.BLOOD, 47, "F", 1L, null, 1L,
                null, Ward.ICU, List.of(), null, null);

        assertThat(validator.validate(sinResultados)).extracting(v -> v.getMessage())
                .containsExactly("Registre al menos un antibiótico del antibiograma");
    }
}
