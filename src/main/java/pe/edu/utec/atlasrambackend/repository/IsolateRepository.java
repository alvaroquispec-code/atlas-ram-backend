package pe.edu.utec.atlasrambackend.repository;

import pe.edu.utec.atlasrambackend.model.Isolate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface IsolateRepository extends JpaRepository<Isolate, Long> {

    Page<Isolate> findByFacilityId(Long facilityId, Pageable pageable);

    List<Isolate> findByCollectionDateBetween(LocalDate from, LocalDate to);

    @Query("""
        select i from Isolate i
        join fetch i.facility
        join fetch i.microorganism
        where i.id = :id
        """)
    Isolate findByIdWithRelations(Long id);
}