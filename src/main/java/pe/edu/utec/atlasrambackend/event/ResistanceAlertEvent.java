package pe.edu.utec.atlasrambackend.event;

public record ResistanceAlertEvent(
        Long facilityId,
        String facilityName,
        String microorganismName,
        String antibioticName,
        double resistancePercentage,
        long totalTested
) {}

