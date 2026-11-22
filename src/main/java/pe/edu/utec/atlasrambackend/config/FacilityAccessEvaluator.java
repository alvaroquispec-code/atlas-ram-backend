package pe.edu.utec.atlasrambackend.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import pe.edu.utec.atlasrambackend.repository.IsolateRepository;
import pe.edu.utec.atlasrambackend.repository.UserRepository;

import java.util.Optional;
import java.util.Set;

@Component("facilityAccess")
public class FacilityAccessEvaluator {

    private final UserRepository userRepository;
    private final IsolateRepository isolateRepository;

    public FacilityAccessEvaluator(UserRepository userRepository, IsolateRepository isolateRepository) {
        this.userRepository = userRepository;
        this.isolateRepository = isolateRepository;
    }

    public boolean canAccess(Authentication authentication, Long facilityId) {
        if (authentication == null || !authentication.isAuthenticated() || facilityId == null) {
            return false;
        }
        if (hasAnyRole(authentication, "ROLE_ADMIN", "ROLE_EPIDEMIOLOGIST")) {
            return true;
        }
        return userRepository.isAssignedToFacility(authentication.getName(), facilityId);
    }

    /**
     * Mismo control que canAccess, pero a partir de un aislamiento: un técnico solo agrega
     * resultados a aislamientos de sus establecimientos. Si el aislamiento no existe se deja
     * pasar para que el servicio responda 404.
     */
    public boolean canAccessIsolate(Authentication authentication, Long isolateId) {
        if (authentication == null || !authentication.isAuthenticated() || isolateId == null) {
            return false;
        }
        if (hasAnyRole(authentication, "ROLE_ADMIN", "ROLE_EPIDEMIOLOGIST")) {
            return true;
        }
        return isolateRepository.findFacilityIdById(isolateId)
                .map(facilityId -> userRepository.isAssignedToFacility(authentication.getName(), facilityId))
                .orElse(true);
    }

    /**
     * Establecimientos cuyos aislamientos puede listar el usuario. Vacío significa sin restricción
     * (ADMIN y EPIDEMIOLOGIST ven toda la red); un técnico solo ve los establecimientos asignados.
     */
    public Optional<Set<Long>> visibleFacilityIds(Authentication authentication) {
        if (hasAnyRole(authentication, "ROLE_ADMIN", "ROLE_EPIDEMIOLOGIST")) {
            return Optional.empty();
        }
        return Optional.of(userRepository.findFacilityIdsByEmail(authentication.getName()));
    }

    private boolean hasAnyRole(Authentication authentication, String... roles) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            for (String role : roles) {
                if (role.equals(authority.getAuthority())) {
                    return true;
                }
            }
        }
        return false;
    }
}

