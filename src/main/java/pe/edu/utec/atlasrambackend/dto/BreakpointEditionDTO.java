package pe.edu.utec.atlasrambackend.dto;

/** Una edición de puntos de corte cargada en el sistema. */
public record BreakpointEditionDTO(String standard, String version, long breakpoints, boolean active) {}
