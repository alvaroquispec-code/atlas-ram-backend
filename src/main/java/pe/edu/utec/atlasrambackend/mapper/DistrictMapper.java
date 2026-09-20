package pe.edu.utec.atlasrambackend.mapper;

import org.springframework.stereotype.Component;
import pe.edu.utec.atlasrambackend.dto.CreateDistrictDTO;
import pe.edu.utec.atlasrambackend.dto.DistrictResponseDTO;
import pe.edu.utec.atlasrambackend.model.District;

@Component
public class DistrictMapper {

    public DistrictResponseDTO toResponse(District district) {
        return new DistrictResponseDTO(
                district.getId(),
                district.getUbigeo(),
                district.getName(),
                district.getProvince(),
                district.getDepartment()
        );
    }

    public District toEntity(CreateDistrictDTO dto) {
        District district = new District();
        district.setUbigeo(dto.ubigeo());
        district.setName(dto.name());
        district.setProvince(dto.province());
        district.setDepartment(dto.department());
        return district;
    }
}