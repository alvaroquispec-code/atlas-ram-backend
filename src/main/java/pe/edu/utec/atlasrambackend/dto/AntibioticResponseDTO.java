package pe.edu.utec.atlasrambackend.dto;

import pe.edu.utec.atlasrambackend.model.AwareCategory;

public record AntibioticResponseDTO(
        Long id,
        String code,
        String name,
        String antibioticClass,
        AwareCategory awareCategory
) {}