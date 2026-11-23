package pe.edu.utec.atlasrambackend.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utec.atlasrambackend.model.StockLot;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockLotRepository extends JpaRepository<StockLot, Long> {

    /** Lotes con saldo de los ítems de un establecimiento (para stock disponible y riesgo de vencimiento). */
    @Query("""
            select l from StockLot l
            where l.stockItem.facility.id = :facilityId and l.quantityOnHand > 0
            order by l.expiryDate, l.id
            """)
    List<StockLot> findOnHandByFacility(@Param("facilityId") Long facilityId);

    @Query("""
            select l from StockLot l
            where l.stockItem.id = :itemId and l.quantityOnHand > 0
            order by l.expiryDate, l.id
            """)
    List<StockLot> findOnHandByItem(@Param("itemId") Long itemId);

    /**
     * Lotes utilizables en una fecha, en orden FEFO, bloqueados para escritura: dos consumos simultáneos
     * no pueden descontar el mismo saldo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from StockLot l
            where l.stockItem.id = :itemId and l.quantityOnHand > 0 and l.expiryDate >= :date
            order by l.expiryDate, l.id
            """)
    List<StockLot> findUsableForUpdate(@Param("itemId") Long itemId, @Param("date") LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from StockLot l join fetch l.stockItem where l.id = :id")
    Optional<StockLot> findByIdForUpdate(@Param("id") Long id);

    boolean existsByStockItemIdAndLotNumberIgnoreCase(Long itemId, String lotNumber);

    @Query("select l.stockItem.facility.id from StockLot l where l.id = :id")
    Optional<Long> findFacilityIdById(@Param("id") Long id);
}
