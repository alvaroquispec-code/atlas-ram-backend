package pe.edu.utec.atlasrambackend.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import pe.edu.utec.atlasrambackend.repository.UserRepository;

@Component("facilityAccess")
public class FacilityAccessEvaluator {

    private final UserRepository userRepository;

    public FacilityAccessEvaluator(UserRepository userRepository) {
        this.userRepository = userRepository;
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

