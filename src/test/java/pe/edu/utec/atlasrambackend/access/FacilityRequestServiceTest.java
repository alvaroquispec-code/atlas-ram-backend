package pe.edu.utec.atlasrambackend.access;

import org.junit.jupiter.api.Test;
import pe.edu.utec.atlasrambackend.model.FacilitySector;
import pe.edu.utec.atlasrambackend.model.User;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FacilityRequestServiceTest {

    @Test
    void validaElDigitoVerificadorDelRuc() {
        assertThat(FacilityRequestService.isValidRuc("20131257750")).isTrue();
        assertThat(FacilityRequestService.isValidRuc("20101234567")).isFalse();
        assertThat(FacilityRequestService.isValidRuc("2013125775")).isFalse();
    }

    @Test
    void reconoceLosDominiosDelEstado() {
        assertThat(FacilityRequestService.isStateDomain("essalud.gob.pe")).isTrue();
        assertThat(FacilityRequestService.isStateDomain("hospitalloayza.gob.pe")).isTrue();
        assertThat(FacilityRequestService.isStateDomain("clinicaejemplo.com.pe")).isFalse();
        assertThat(FacilityRequestService.isStateDomain("gob.pe.falso.com")).isFalse();
    }

    @Test
    void comparaNombresSinTildesNiConectores() {
        assertThat(FacilityRequestService.namesMatch("Hospital Nacional Cayetano Heredia",
                "HOSPITAL NACIONAL CAYETANO HEREDIA")).isTrue();
        assertThat(FacilityRequestService.namesMatch("Clínica San Felipe", "CLINICA SAN FELIPE S.A.")).isTrue();
        assertThat(FacilityRequestService.namesMatch("Clínica San Felipe", "HOSPITAL MARIA AUXILIADORA")).isFalse();
    }

    private static Renipress entry(boolean publicSector) {
        Renipress e = new Renipress();
        e.setCode("00006210");
        e.setName("CLINICA SAN FELIPE");
        e.setInstitution(publicSector ? "MINSA" : "PRIVADO");
        e.setStatus("ACTIVADO");
        e.setPublicSector(publicSector);
        return e;
    }

    private static FacilityRequest request(FacilitySector claimed, String email, boolean verified) {
        User u = new User();
        u.setEmail(email);
        if (verified) {
            u.setEmailVerifiedAt(Instant.now());
        }
        FacilityRequest r = new FacilityRequest();
        r.setRequester(u);
        r.setName("Clínica San Felipe");
        r.setRuc("20100047218");
        r.setRenipressCode("00006210");
        r.setClaimedSector(claimed);
        return r;
    }

    @Test
    void unaPrivadaQueDiceSerPublicaQuedaComoPrivada() {
        FacilityRequest r = request(FacilitySector.PUBLIC, "gerencia@clinica.com.pe", true);
        Optional<Renipress> e = Optional.of(entry(false));
        assertThat(FacilityRequestService.sectorOf(e)).isEqualTo(FacilitySector.PRIVATE);
        assertThat(FacilityRequestService.qualifiesForAutoApproval(r, e)).isFalse();
        assertThat(FacilityRequestService.checks(r, e))
                .anyMatch(c -> c.startsWith("Declaró ser PÚBLICO pero RENIPRESS lo registra como PRIVADO"));
    }

    @Test
    void sinRenipressNuncaSeReconoceComoPublica() {
        assertThat(FacilityRequestService.sectorOf(Optional.empty())).isEqualTo(FacilitySector.PRIVATE);
    }

    @Test
    void unaPublicaConCorreoVerificadoDelEstadoSeApruebaSola() {
        FacilityRequest r = request(FacilitySector.PUBLIC, "director@hospital.gob.pe", true);
        assertThat(FacilityRequestService.qualifiesForAutoApproval(r, Optional.of(entry(true)))).isTrue();
        FacilityRequest sinVerificar = request(FacilitySector.PUBLIC, "director@hospital.gob.pe", false);
        assertThat(FacilityRequestService.qualifiesForAutoApproval(sinVerificar, Optional.of(entry(true)))).isFalse();
    }
}
