package pe.edu.utec.atlasrambackend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.edu.utec.atlasrambackend.model.User;
import pe.edu.utec.atlasrambackend.repository.UserRepository;

import java.io.IOException;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final SecurityExceptionHandler securityExceptionHandler;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   UserRepository userRepository,
                                   SecurityExceptionHandler securityExceptionHandler) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.securityExceptionHandler = securityExceptionHandler;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return "/auth/login".equals(path) || "/auth/refresh".equals(path);
    }

    /**
     * Sin encabezado Authorization la petición sigue como anónima (los endpoints públicos responden).
     * Con un token presentado, este debe ser válido: si está mal firmado, venció o su sesión fue revocada,
     * se responde 401 aunque el endpoint sea público. Así el cliente se entera en su siguiente petición,
     * sea cual sea, intenta renovar y, si la sesión fue cerrada, vuelve al login.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)
                || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }
        Optional<User> user = jwtService.parseAccessToken(header.substring(BEARER_PREFIX.length()))
                .flatMap(subject -> userRepository.findByEmail(subject.email())
                        .filter(u -> u.isActive() && u.getTokenVersion() == subject.version()));
        if (user.isEmpty()) {
            securityExceptionHandler.rejectToken(request, response);
            return;
        }
        UserDetails userDetails = CustomUserDetailsService.toUserDetails(user.get());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }
}
