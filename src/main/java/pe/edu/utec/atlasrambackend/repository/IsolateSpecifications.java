package pe.edu.utec.atlasrambackend.repository;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.edu.utec.atlasrambackend.model.Isolate;
import pe.edu.utec.atlasrambackend.model.Specimen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Filtros opcionales del listado de aislamientos. Cada filtro en null no restringe nada,
 * así una sola consulta cubre cualquier combinación sin escribir un método por caso.
 */
public final class IsolateSpecifications {

    private IsolateSpecifications() {
    }

    /**
     * @param allowedFacilityIds establecimientos visibles para el usuario; null no restringe
     *                           y una colección vacía no devuelve ningún aislamiento.
     */
    public static Specification<Isolate> withFilters(Long facilityId, Long microorganismId, Specimen specimen,
                                                     Collection<Long> allowedFacilityIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (allowedFacilityIds != null) {
                predicates.add(allowedFacilityIds.isEmpty()
                        ? cb.disjunction()
                        : root.get("facility").get("id").in(allowedFacilityIds));
            }
            if (facilityId != null) {
                predicates.add(cb.equal(root.get("facility").get("id"), facilityId));
            }
            if (microorganismId != null) {
                predicates.add(cb.equal(root.get("microorganism").get("id"), microorganismId));
            }
            if (specimen != null) {
                predicates.add(cb.equal(root.get("specimenType"), specimen));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
