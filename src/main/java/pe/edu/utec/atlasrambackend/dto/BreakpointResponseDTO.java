package pe.edu.utec.atlasrambackend.dto;


import java.math.BigDecimal;


public record BreakpointResponseDTO(
        Long id,
        Long microorganismId,
        String microorganismCode,
        Long antibioticId,
        String antibioticCode,
        String standard,
        String version,
        BigDecimal susceptibleMax,
        BigDecimal resistantMin
) {}

