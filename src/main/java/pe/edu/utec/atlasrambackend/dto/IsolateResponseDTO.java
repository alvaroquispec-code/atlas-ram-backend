package pe.edu.utec.atlasrambackend.dto;

import pe.edu.utec.atlasrambackend.model.Specimen;

import java.time.LocalDate;

public record IsolateResponseDTO(
        Long id,
        LocalDate collectionDate,
        Specimen specimenType,
        Integer patientAge,
        String patientSex,
        String facilityName,
        String districtUbigeo,
        String districtName,
        String microorganismName,
        Long dataUploadId
) {}