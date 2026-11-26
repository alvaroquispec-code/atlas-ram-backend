package pe.edu.utec.atlasrambackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityExceptionHandler securityExceptionHandler;
    private final List<String> allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          SecurityExceptionHandler securityExceptionHandler,
                          @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.securityExceptionHandler = securityExceptionHandler;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * El filtro JWT es un @Component; sin esto Spring Boot lo registraría además como filtro del servidor,
     * fuera de la cadena de seguridad (y fuera del filtro CORS). Solo debe correr dentro de la cadena.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login", "/auth/register", "/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET, "/antibiogram/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/districts/**", "/microorganisms/**",
                                "/antibiotics/**", "/facilities/**", "/standards").permitAll()
                        .requestMatchers("/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/districts/**", "/microorganisms/**",
                                "/antibiotics/**", "/facilities/**", "/breakpoints/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/districts/**", "/microorganisms/**",
                                "/antibiotics/**", "/facilities/**", "/breakpoints/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/uploads/*/revert").hasRole("ADMIN")
                        .requestMatchers("/uploads/**").hasAnyRole("LAB_TECHNICIAN", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/alerts/**")
                        .hasAnyRole("LAB_TECHNICIAN", "EPIDEMIOLOGIST", "ADMIN")
                        .requestMatchers("/alerts/**").hasAnyRole("EPIDEMIOLOGIST", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/fhir/**")
                        .hasAnyRole("LAB_TECHNICIAN", "EPIDEMIOLOGIST", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/isolates/**")
                        .hasAnyRole("LAB_TECHNICIAN", "EPIDEMIOLOGIST", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/isolates/**", "/results/**")
                        .hasAnyRole("LAB_TECHNICIAN", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/logistics/**")
                        .hasAnyRole("PHARMACIST", "EPIDEMIOLOGIST", "ADMIN")
                        .requestMatchers("/logistics/**").hasAnyRole("PHARMACIST", "ADMIN")
                        .requestMatchers("/audit-log/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/reports/**")
                        .hasAnyRole("LAB_TECHNICIAN", "EPIDEMIOLOGIST", "ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}




