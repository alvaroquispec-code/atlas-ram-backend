package pe.edu.utec.atlasrambackend.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utec.atlasrambackend.model.StockItem;

import java.util.List;
import java.util.Optional;

public interface StockItemRepository extends JpaRepository<StockItem, Long> {

    @EntityGraph(attributePaths = {"antibiotic", "facility"})
    List<StockItem> findByFacilityIdAndActiveTrueOrderByIdAsc(Long facilityId);

    boolean existsByFacilityIdAndAntibioticIdAndPresentationIgnoreCase(Long facilityId, Long antibioticId,
                                                                      String presentation);

    @Query("select s.facility.id from StockItem s where s.id = :id")
    Optional<Long> findFacilityIdById(@Param("id") Long id);
}
