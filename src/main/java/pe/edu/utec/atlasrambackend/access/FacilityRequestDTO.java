package pe.edu.utec.atlasrambackend.access;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pe.edu.utec.atlasrambackend.model.FacilitySector;

/** Pedido de registro de un establecimiento. */
public record FacilityRequestDTO(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Pattern(regexp = "^(10|15|16|17|20)[0-9]{9}$", message = "El RUC tiene 11 dígitos") String ruc,
        @NotBlank @Pattern(regexp = "^[0-9]{8}$", message = "El código IPRESS tiene 8 dígitos") String renipressCode,
        @NotNull Long districtId,
        @NotNull FacilitySector claimedSector
) {}
