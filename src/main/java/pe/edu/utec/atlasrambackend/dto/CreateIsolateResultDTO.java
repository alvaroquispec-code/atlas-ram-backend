package pe.edu.utec.atlasrambackend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import pe.edu.utec.atlasrambackend.model.Interpretation;

/**
 * Un antibiótico del antibiograma, tal como lo informa el laboratorio. La CIM va como texto ("<=0.25", ">=16",
 * "0,5", "2/38") y se interpreta con el mismo lector que las cargas CSV y WHONET (MicParser), así un resultado
 * cargado a mano y uno importado se guardan igual.
 */
public record CreateIsolateResultDTO(
        @NotNull(message = "Indique el antibiótico") Long antibioticId,
        String mic,
        @Positive @Max(value = 99, message = "El halo debe estar entre 1 y 99 mm") Integer diskMm,
        /** Solo se usa si la CIM o el halo no bastan para interpretar con el punto de corte vigente. */
        Interpretation reportedInterpretation
) {}
