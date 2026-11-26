package pe.edu.utec.atlasrambackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Antibióticos que se agregan a un aislamiento ya registrado (por ejemplo, resultados que llegan después). */
public record AddResultsDTO(
        @NotEmpty(message = "Agregue al menos un antibiótico")
        List<@Valid CreateIsolateResultDTO> results
) {}
