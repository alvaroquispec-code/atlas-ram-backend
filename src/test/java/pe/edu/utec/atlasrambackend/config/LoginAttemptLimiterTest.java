package pe.edu.utec.atlasrambackend.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import pe.edu.utec.atlasrambackend.exception.TooManyAttemptsException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptLimiterTest {

    /** Reloj que se puede adelantar a mano. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-28T15:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(true, 5, 30, Duration.ofMinutes(15), clock);

    @Test
    void bloqueaUnCorreoTrasCincoFallosYLoLiberaAlVencerLaVentana() {
        for (int i = 0; i < 5; i++) {
            limiter.checkAllowed("Lab@Atlas.test", "1.1.1.1");
            limiter.recordFailure("lab@atlas.test", "1.1.1.1");
        }
        // Mismo correo desde otra IP: sigue bloqueado (el límite es por cuenta).
        assertThatThrownBy(() -> limiter.checkAllowed("lab@atlas.test", "2.2.2.2"))
                .isInstanceOf(TooManyAttemptsException.class);

        clock.now = clock.now.plus(Duration.ofMinutes(16));
        assertThatCode(() -> limiter.checkAllowed("lab@atlas.test", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void elExitoBorraLosFallosDelCorreoYOtrosUsuariosDeLaMismaIpNoSeAfectan() {
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("a@atlas.test", "10.0.0.1");
        }
        limiter.recordSuccess("a@atlas.test");
        limiter.recordFailure("a@atlas.test", "10.0.0.1");
        assertThatCode(() -> limiter.checkAllowed("a@atlas.test", "10.0.0.1")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.checkAllowed("b@atlas.test", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void bloqueaUnaIpQuePruebaMuchasCuentas() {
        for (int i = 0; i < 30; i++) {
            limiter.recordFailure("user" + i + "@atlas.test", "6.6.6.6");
        }
        assertThatThrownBy(() -> limiter.checkAllowed("nuevo@atlas.test", "6.6.6.6"))
                .isInstanceOf(TooManyAttemptsException.class);
        assertThatCode(() -> limiter.checkAllowed("nuevo@atlas.test", "7.7.7.7")).doesNotThrowAnyException();
    }

    @Test
    void apagadoNoBloqueaNunca() {
        LoginAttemptLimiter off = new LoginAttemptLimiter(false, 1, 1, Duration.ofMinutes(15), clock);
        off.recordFailure("x@atlas.test", "1.1.1.1");
        off.recordFailure("x@atlas.test", "1.1.1.1");
        assertThatCode(() -> off.checkAllowed("x@atlas.test", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void tomaLaIpQueAgregaElProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "9.9.9.9, 200.48.1.20");
        assertThat(LoginAttemptLimiter.clientIp(request)).isEqualTo("200.48.1.20");

        MockHttpServletRequest direct = new MockHttpServletRequest();
        direct.setRemoteAddr("200.48.1.21");
        assertThat(LoginAttemptLimiter.clientIp(direct)).isEqualTo("200.48.1.21");
    }
}
