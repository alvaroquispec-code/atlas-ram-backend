package pe.edu.utec.atlasrambackend.mapper;

import org.springframework.stereotype.Component;
import pe.edu.utec.atlasrambackend.dto.FacilityResponseDTO;
import pe.edu.utec.atlasrambackend.model.Facility;

@Component
public class FacilityMapper {

    public FacilityResponseDTO toResponse(Facility f) {
        return new FacilityResponseDTO(
                f.getId(),
                f.getCode(),
                f.getName(),
                f.getDistrict().getId(),
                f.getDistrict().getName()
        );
    }
}