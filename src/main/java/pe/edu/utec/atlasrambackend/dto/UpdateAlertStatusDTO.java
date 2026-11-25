package pe.edu.utec.atlasrambackend.dto;

import jakarta.validation.constraints.NotNull;
import pe.edu.utec.atlasrambackend.model.AlertStatus;

public record UpdateAlertStatusDTO(@NotNull AlertStatus status) {}
