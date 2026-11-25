package pe.edu.utec.atlasrambackend.dto;

import java.util.List;

/** Resultado de revertir una carga: qué se borró y qué alertas se retiraron. */
public record UploadRevertResponseDTO(
        DataUploadResponseDTO upload,
        int isolatesRemoved,
        int resultsRemoved,
        List<Long> withdrawnAlertIds
) {}
