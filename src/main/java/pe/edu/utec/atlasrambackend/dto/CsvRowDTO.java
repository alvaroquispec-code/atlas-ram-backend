package pe.edu.utec.atlasrambackend.dto;

/**
 * Una fila del CSV de carga, con los valores tal como llegaron (recortados). Las columnas opcionales
 * que el archivo no trae llegan como cadena vacía.
 */
public record CsvRowDTO(
        int lineNumber,
        String collectionDate,
        String specimenType,
        String patientAge,
        String patientSex,
        String facilityCode,
        String residenceUbigeo,
        String microorganismCode,
        String antibioticCode,
        String micValue,
        String reportedInterpretation,
        String patientId,
        String labIsolateId
) {}
