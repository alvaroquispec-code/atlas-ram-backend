package pe.edu.utec.atlasrambackend.dto;

/** Cuántos aislamientos de pacientes de un distrito se registraron en cada establecimiento. */
public record ReferralFacilityRow(
        Long facilityId,
        String facilityName,
        long isolateCount
) {}
