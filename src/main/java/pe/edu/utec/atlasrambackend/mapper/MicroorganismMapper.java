package pe.edu.utec.atlasrambackend.mapper;

import org.springframework.stereotype.Component;
import pe.edu.utec.atlasrambackend.dto.CreateMicroorganismDTO;
import pe.edu.utec.atlasrambackend.dto.MicroorganismResponseDTO;
import pe.edu.utec.atlasrambackend.model.GramStain;
import pe.edu.utec.atlasrambackend.model.Microorganism;

@Component
public class MicroorganismMapper {

    public MicroorganismResponseDTO toResponse(Microorganism m) {
        return new MicroorganismResponseDTO(
                m.getId(),
                m.getCode(),
                m.getGenus(),
                m.getSpecies(),
                m.getGramStain() == null ? null : m.getGramStain().name()
        );
    }

    public Microorganism toEntity(CreateMicroorganismDTO dto) {
        Microorganism m = new Microorganism();
        m.setCode(dto.code());
        m.setGenus(dto.genus());
        m.setSpecies(dto.species());
        m.setGramStain(dto.gramStain() == null ? null : GramStain.valueOf(dto.gramStain()));
        return m;
    }
}