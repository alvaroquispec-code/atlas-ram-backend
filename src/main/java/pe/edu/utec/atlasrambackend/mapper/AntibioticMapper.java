package pe.edu.utec.atlasrambackend.mapper;

import org.springframework.stereotype.Component;
import pe.edu.utec.atlasrambackend.dto.CreateAntibioticDTO;
import pe.edu.utec.atlasrambackend.dto.AntibioticResponseDTO;
import pe.edu.utec.atlasrambackend.model.Antibiotic;

@Component
public class AntibioticMapper {

    public AntibioticResponseDTO toResponse(Antibiotic a) {
        return new AntibioticResponseDTO(
                a.getId(),
                a.getCode(),
                a.getName(),
                a.getAntibioticClass()
        );
    }

    public Antibiotic toEntity(CreateAntibioticDTO dto) {
        Antibiotic a = new Antibiotic();
        a.setCode(dto.code());
        a.setName(dto.name());
        a.setAntibioticClass(dto.antibioticClass());
        return a;
    }
}