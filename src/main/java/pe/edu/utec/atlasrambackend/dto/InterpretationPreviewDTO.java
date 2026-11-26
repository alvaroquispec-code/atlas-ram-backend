package pe.edu.utec.atlasrambackend.dto;

import pe.edu.utec.atlasrambackend.model.Interpretation;
import pe.edu.utec.atlasrambackend.model.InterpretationSource;

/**
 * Interpretación que tendría un resultado antes de guardarlo, con la misma lógica que el registro.
 * interpretation es null si con esos datos no se puede interpretar; message explica por qué.
 */
public record InterpretationPreviewDTO(
        Interpretation interpretation,
        InterpretationSource source,
        String message
) {}
