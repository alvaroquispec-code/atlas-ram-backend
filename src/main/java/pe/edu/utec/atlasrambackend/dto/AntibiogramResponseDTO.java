package pe.edu.utec.atlasrambackend.dto;

public record AntibiogramResponseDTO(
        String microorganismName,
        String antibioticName,
        long totalTested,
        long resistantCount,
        double resistancePercentage
) {}