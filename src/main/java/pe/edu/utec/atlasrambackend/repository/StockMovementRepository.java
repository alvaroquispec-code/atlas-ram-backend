package pe.edu.utec.atlasrambackend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.atlasrambackend.model.StockMovement;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    @EntityGraph(attributePaths = {"lot"})
    Page<StockMovement> findByStockItemIdOrderByMovementDateDescIdDesc(Long itemId, Pageable pageable);
}
