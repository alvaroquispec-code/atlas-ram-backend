package pe.edu.utec.atlasrambackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import pe.edu.utec.atlasrambackend.model.DataOrigin;
import pe.edu.utec.atlasrambackend.model.Specimen;
import pe.edu.utec.atlasrambackend.model.Ward;

import java.time.LocalDate;
import java.util.List;

/**
 * Registro manual de un aislamiento con su antibiograma (un paciente a la vez; la carga masiva es /uploads). Sexo, edad y servicio son obligatorios porque el INS los exige en sus
 * "Variables mínimas obligatorias" para la vigilancia nacional en WHONET-INS (SEX, AGE, DEPARTMENT/TYPE_WARD):
 * quien registra tiene al paciente delante. Las cargas masivas (CSV o WHONET) los aceptan vacíos para no perder
 * el antibiograma, pero informan su completitud (ver DataCompletenessDTO).
 */
public record CreateIsolateDTO(
        @NotNull @PastOrPresent LocalDate collectionDate,
        @NotNull Specimen specimenType,
        @NotNull(message = "Indique la edad del paciente")
        @Min(value = 0, message = "La edad no puede ser negativa")
        @Max(value = 120, message = "La edad debe estar entre 0 y 120 años")
        Integer patientAge,
        @NotNull(message = "Indique el sexo del paciente")
        @Pattern(regexp = "[MF]", message = "El sexo debe ser M o F")
        String patientSex,
        @NotNull Long facilityId,
        Long districtId,
        @NotNull Long microorganismId,
        /** REAL o SYNTHETIC; si no se envía, SYNTHETIC (nunca se presume que un dato es real). */
        DataOrigin dataOrigin,
        /** Servicio del paciente (consulta externa, emergencia, hospitalización, UCI). */
        @NotNull(message = "Indique el servicio del paciente")
        Ward ward,
        /**
         * Antibiograma del aislamiento. Se guarda junto con el aislamiento en una sola transacción: si un resultado
         * no se puede interpretar, no queda un aislamiento sin antibiograma.
         */
        @NotEmpty(message = "Registre al menos un antibiótico del antibiograma")
        List<@Valid CreateIsolateResultDTO> results
) {}
