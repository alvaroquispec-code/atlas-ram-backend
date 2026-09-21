package pe.edu.utec.atlasrambackend.dto;

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
        String reportedInterpretation
) {}


