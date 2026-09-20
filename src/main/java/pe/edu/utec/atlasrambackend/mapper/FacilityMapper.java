package pe.edu.utec.atlasrambackend.mapper;

import org.springframework.stereotype.Component;
import pe.edu.utec.atlasrambackend.dto.CreateFacilityDTO;
import pe.edu.utec.atlasrambackend.dto.FacilityResponseDTO;
import pe.edu.utec.atlasrambackend.model.District;
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

    public Facility toEntity(CreateFacilityDTO dto, District district) {
        Facility facility = new Facility();
        facility.setCode(dto.code());
        facility.setName(dto.name());
        facility.setDistrict(district);
        return facility;
    }
}
