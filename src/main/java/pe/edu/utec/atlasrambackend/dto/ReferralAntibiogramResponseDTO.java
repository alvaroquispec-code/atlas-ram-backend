package pe.edu.utec.atlasrambackend.dto;

import java.util.List;

/**
 * Antibiograma de los hospitales de referencia de un distrito: cuando el distrito no reúne
 * 30 resultados por combinación, se usa la resistencia de los hospitales donde se atienden sus pacientes.
 */
public record ReferralAntibiogramResponseDTO(
        String districtName,
        List<ReferralFacilityDTO> facilities,
        List<AntibiogramResponseDTO> entries
) {}
