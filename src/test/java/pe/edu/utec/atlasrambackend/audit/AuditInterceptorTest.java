package pe.edu.utec.atlasrambackend.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AuditInterceptorTest {

    private final AuditLog auditLog = mock(AuditLog.class);
    private final AuditInterceptor interceptor = new AuditInterceptor(auditLog);

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registraEscriturasYLecturasDeDatosIndividuales() {
        assertThat(AuditInterceptor.shouldAudit("POST", "/uploads")).isTrue();
        assertThat(AuditInterceptor.shouldAudit("DELETE", "/isolates/7")).isTrue();
        assertThat(AuditInterceptor.shouldAudit("GET", "/isolates/7")).isTrue();
        assertThat(AuditInterceptor.shouldAudit("GET", "/fhir/Observation")).isTrue();
    }

    @Test
    void noRegistraAgregadosNiCatalogos() {
        assertThat(AuditInterceptor.shouldAudit("GET", "/antibiogram/by-district")).isFalse();
        assertThat(AuditInterceptor.shouldAudit("GET", "/districts")).isFalse();
        assertThat(AuditInterceptor.shouldAudit("GET", "/isolatesx")).isFalse();
        assertThat(AuditInterceptor.shouldAudit("OPTIONS", "/isolates")).isFalse();
    }

    @Test
    void guardaUsuarioRutaSinPrefijoNiConsultaYCodigo() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "lab.hnch@atlasram.test", null, List.of(new SimpleGrantedAuthority("ROLE_LAB_TECHNICIAN"))));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/isolates/9172");
        request.setContextPath("/api/v1");
        request.setQueryString("patientId=123");
        request.setRemoteAddr("200.48.1.20");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(auditLog).record("lab.hnch@atlasram.test", "GET", "/isolates/9172", 200, "200.48.1.20");
    }

    @Test
    void ignoraLecturasDeAgregados() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/antibiogram");
        request.setContextPath("/api/v1");
        interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);
        verify(auditLog, never()).record(any(), anyString(), anyString(), anyInt(), any());
    }
}
