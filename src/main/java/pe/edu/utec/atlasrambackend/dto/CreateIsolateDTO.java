package pe.edu.utec.atlasrambackend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import pe.edu.utec.atlasrambackend.model.Specimen;

import java.time.LocalDate;

public record CreateIsolateDTO(
        @NotNull @PastOrPresent LocalDate collectionDate,
        @NotNull Specimen specimenType,
        Integer patientAge,
        String patientSex,
        @NotNull Long facilityId,
        Long districtId,
        @NotNull Long microorganismId
) {}