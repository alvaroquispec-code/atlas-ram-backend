package pe.edu.utec.atlasrambackend.mapper;

import org.springframework.stereotype.Component;
import pe.edu.utec.atlasrambackend.dto.IsolateResponseDTO;
import pe.edu.utec.atlasrambackend.model.Isolate;
import pe.edu.utec.atlasrambackend.model.Microorganism;

@Component
public class IsolateMapper {

    public IsolateResponseDTO toResponse(Isolate i) {
        Microorganism m = i.getMicroorganism();
        String organismName = m.getSpecies() == null
                ? m.getGenus()
                : m.getGenus() + " " + m.getSpecies();

        return new IsolateResponseDTO(
                i.getId(),
                i.getCollectionDate(),
                i.getSpecimenType(),
                i.getPatientAge(),
                i.getPatientSex(),
                i.getFacility().getName(),
                organismName
        );
    }
}