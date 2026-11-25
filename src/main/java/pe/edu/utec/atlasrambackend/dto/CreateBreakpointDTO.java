package pe.edu.utec.atlasrambackend.dto;

import pe.edu.utec.atlasrambackend.model.BreakpointSite;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;


import java.math.BigDecimal;


public record CreateBreakpointDTO(
        @NotNull Long microorganismId,
        @NotNull Long antibioticId,
        @NotBlank String standard,
        @NotBlank String version,
        @Positive BigDecimal susceptibleMax,
        @Positive BigDecimal resistantMin,
        /** Disco (mm): S si el halo ≥ este valor. Opcional. */
        @Positive Integer diskSusceptibleMin,
        /** Disco (mm): R si el halo ≤ este valor. Opcional. */
        @Positive Integer diskResistantMax,
        /** Sitio de infección: ANY (por defecto), MENINGITIS o NON_MENINGITIS. */
        BreakpointSite site
) {
    public BreakpointSite siteOrDefault() {
        return site == null ? BreakpointSite.ANY : site;
    }
}


