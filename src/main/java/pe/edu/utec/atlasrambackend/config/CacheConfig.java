package pe.edu.utec.atlasrambackend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Caché en memoria (Caffeine) de las consultas públicas de agregados: antibiograma, mapa por distrito,
 * tendencias, WISCA y datos publicados.
 *
 * Por qué: son las consultas más pedidas y las más caras (agregan miles de resultados; WISCA corre una
 * simulación Monte Carlo), y su resultado solo cambia cuando entran o se corrigen datos. Con la caché, la
 * segunda vez que alguien abre el mismo mapa no toca la base de datos.
 *
 * Cómo se mantiene al día: cualquier escritura correcta (POST, PUT, PATCH o DELETE con respuesta 2xx o 3xx)
 * vacía la caché, y la carga de CSV, que se procesa en segundo plano, la vuelve a vaciar al terminar
 * (UploadService): así lo cargado se ve en el mapa al instante. Además cada entrada vence a los
 * 2 minutos y hay como máximo 500 por consulta (application.properties). Con varias instancias, cada una
 * tiene su caché: el vencimiento de 2 minutos acota cuánto puede atrasarse una instancia que no recibió la
 * escritura; a esa escala la caché pasaría a Redis (ElastiCache).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Set<String> READ_ONLY = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    /** Vacía todas las cachés de agregados (también la llama la carga de CSV al terminar en segundo plano). */
    @Bean
    public AggregateCache aggregateCache(CacheManager cacheManager) {
        return () -> {
            for (String name : cacheManager.getCacheNames()) {
                Cache cache = cacheManager.getCache(name);
                if (cache != null) {
                    cache.clear();
                }
            }
        };
    }

    @Bean
    public OncePerRequestFilter cacheEvictionOnWriteFilter(AggregateCache aggregateCache) {
        return new OncePerRequestFilter() {
            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                return READ_ONLY.contains(request.getMethod().toUpperCase());
            }

            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                try {
                    chain.doFilter(request, response);
                } finally {
                    if (response.getStatus() < 400) {
                        aggregateCache.clearAll();
                    }
                }
            }
        };
    }

    /** Punto único para invalidar los agregados en caché. */
    @FunctionalInterface
    public interface AggregateCache {
        void clearAll();
    }
}
