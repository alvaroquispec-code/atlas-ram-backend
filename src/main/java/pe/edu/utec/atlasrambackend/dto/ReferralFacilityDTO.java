package pe.edu.utec.atlasrambackend.dto;

/** Establecimiento donde se atienden los pacientes de un distrito y qué porcentaje de ellos recibe. */
public record ReferralFacilityDTO(
        Long id,
        String name,
        long isolateCount,
        double sharePercentage
) {}
