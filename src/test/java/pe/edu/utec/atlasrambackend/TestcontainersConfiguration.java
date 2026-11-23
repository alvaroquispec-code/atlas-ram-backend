package pe.edu.utec.atlasrambackend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base de datos de las pruebas: un PostgreSQL 16 nuevo y desechable en Docker, la misma versión que producción.
 * Flyway le aplica todas las migraciones desde cero, así que cada corrida prueba también que un despliegue
 * limpio funcione. @ServiceConnection hace que Spring use este contenedor en lugar de la base de desarrollo:
 * las pruebas nunca tocan tus datos locales.
 *
 * Spring arranca el contenedor una vez por contexto de prueba y lo reutiliza en todas las clases que importan
 * esta configuración.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}
