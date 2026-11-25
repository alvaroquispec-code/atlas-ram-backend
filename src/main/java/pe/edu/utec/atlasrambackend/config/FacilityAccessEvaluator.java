package pe.edu.utec.atlasrambackend.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import pe.edu.utec.atlasrambackend.repository.DataUploadRepository;
import pe.edu.utec.atlasrambackend.repository.IsolateRepository;
import pe.edu.utec.atlasrambackend.repository.StockItemRepository;
import pe.edu.utec.atlasrambackend.repository.StockLotRepository;
import pe.edu.utec.atlasrambackend.repository.UserRepository;

import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

@Component("facilityAccess")
public class FacilityAccessEvaluator {

    private final UserRepository userRepository;
    private final IsolateRepository isolateRepository;
    private final DataUploadRepository uploadRepository;
    private final StockItemRepository stockItemRepository;
    private final StockLotRepository stockLotRepository;

    public FacilityAccessEvaluator(UserRepository userRepository, IsolateRepository isolateRepository,
                                   DataUploadRepository uploadRepository, StockItemRepository stockItemRepository,
                                   StockLotRepository stockLotRepository) {
        this.userRepository = userRepository;
        this.isolateRepository = isolateRepository;
        this.uploadRepository = uploadRepository;
        this.stockItemRepository = stockItemRepository;
        this.stockLotRepository = stockLotRepository;
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
        return isolateId != null
                && canAccessOwnedBy(authentication, () -> isolateRepository.findFacilityIdById(isolateId));
    }

    /** Igual, a partir de una carga: un técnico solo ve el estado y los errores de las cargas de sus establecimientos. */
    public boolean canAccessUpload(Authentication authentication, Long uploadId) {
        return uploadId != null
                && canAccessOwnedBy(authentication, () -> uploadRepository.findFacilityIdById(uploadId));
    }

    /** Igual, a partir de un ítem de inventario: un farmacéutico solo opera el inventario de su establecimiento. */
    public boolean canAccessStockItem(Authentication authentication, Long itemId) {
        return itemId != null
                && canAccessOwnedBy(authentication, () -> stockItemRepository.findFacilityIdById(itemId));
    }

    /** Igual, a partir de un lote. */
    public boolean canAccessStockLot(Authentication authentication, Long lotId) {
        return lotId != null
                && canAccessOwnedBy(authentication, () -> stockLotRepository.findFacilityIdById(lotId));
    }

    private boolean canAccessOwnedBy(Authentication authentication, Supplier<Optional<Long>> facilityOf) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (hasAnyRole(authentication, "ROLE_ADMIN", "ROLE_EPIDEMIOLOGIST")) {
            return true;
        }
        return facilityOf.get()
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

