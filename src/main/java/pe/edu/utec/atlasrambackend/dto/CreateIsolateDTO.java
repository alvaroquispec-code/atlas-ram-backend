package pe.edu.utec.atlasrambackend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import pe.edu.utec.atlasrambackend.model.Specimen;

import java.time.LocalDate;
import pe.edu.utec.atlasrambackend.model.DataOrigin;
import pe.edu.utec.atlasrambackend.model.Ward;

public record CreateIsolateDTO(
        @NotNull @PastOrPresent LocalDate collectionDate,
        @NotNull Specimen specimenType,
        Integer patientAge,
        String patientSex,
        @NotNull Long facilityId,
        Long districtId,
        @NotNull Long microorganismId,
        /** REAL o SYNTHETIC; si no se envía, SYNTHETIC (nunca se presume que un dato es real). */
        DataOrigin dataOrigin,
        /** Servicio del paciente (consulta externa, emergencia, hospitalización, UCI). Opcional. */
        Ward ward
) {}