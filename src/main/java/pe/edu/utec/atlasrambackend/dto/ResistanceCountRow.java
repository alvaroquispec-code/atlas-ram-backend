package pe.edu.utec.atlasrambackend.dto;

/** Resultados probados y resistentes de una combinación microorganismo-antibiótico en un periodo. */
public record ResistanceCountRow(
        Long microorganismId,
        Long antibioticId,
        long tested,
        long resistant
) {}
