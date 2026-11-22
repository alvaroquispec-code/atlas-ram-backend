package pe.edu.utec.atlasrambackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.utec.atlasrambackend.model.AlertStatus;
import pe.edu.utec.atlasrambackend.model.ResistanceAlert;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ResistanceAlertRepository extends JpaRepository<ResistanceAlert, Long> {

    /** Alerta activa (abierta o reconocida) de la combinación; a lo sumo una, por el índice único parcial. */
    @Query("""
            select a from ResistanceAlert a
            where a.facility.id = :facilityId
              and a.microorganism.id = :microorganismId
              and a.antibioticClass = :antibioticClass
              and a.status <> pe.edu.utec.atlasrambackend.model.AlertStatus.CLOSED
            """)
    Optional<ResistanceAlert> findActive(@Param("facilityId") Long facilityId,
                                         @Param("microorganismId") Long microorganismId,
                                         @Param("antibioticClass") String antibioticClass);

    @Query("""
            select a from ResistanceAlert a
            join fetch a.facility join fetch a.microorganism join fetch a.antibiotic
            where (:status is null or a.status = :status)
            order by a.createdAt desc
            """)
    List<ResistanceAlert> findAllWithDetails(@Param("status") AlertStatus status);

    @Query("""
            select a from ResistanceAlert a
            join fetch a.facility join fetch a.microorganism join fetch a.antibiotic
            where (:status is null or a.status = :status)
              and a.facility.id in :facilityIds
            order by a.createdAt desc
            """)
    List<ResistanceAlert> findForFacilitiesWithDetails(@Param("status") AlertStatus status,
                                                       @Param("facilityIds") Collection<Long> facilityIds);
}
