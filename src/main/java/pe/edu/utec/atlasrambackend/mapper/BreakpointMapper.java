package pe.edu.utec.atlasrambackend.mapper;

import org.springframework.stereotype.Component;
import pe.edu.utec.atlasrambackend.dto.BreakpointResponseDTO;
import pe.edu.utec.atlasrambackend.dto.CreateBreakpointDTO;
import pe.edu.utec.atlasrambackend.model.Antibiotic;
import pe.edu.utec.atlasrambackend.model.Breakpoint;
import pe.edu.utec.atlasrambackend.model.Microorganism;

@Component
public class BreakpointMapper {

    public BreakpointResponseDTO toResponse(Breakpoint b) {
        return new BreakpointResponseDTO(
                b.getId(),
                b.getMicroorganism().getId(),
                b.getMicroorganism().getCode(),
                b.getAntibiotic().getId(),
                b.getAntibiotic().getCode(),
                b.getStandard(),
                b.getVersion(),
                b.getSusceptibleMax(),
                b.getResistantMin()
        );
    }

    public Breakpoint toEntity(CreateBreakpointDTO dto, Microorganism microorganism, Antibiotic antibiotic) {
        Breakpoint breakpoint = new Breakpoint();
        breakpoint.setMicroorganism(microorganism);
        breakpoint.setAntibiotic(antibiotic);
        breakpoint.setStandard(dto.standard());
        breakpoint.setVersion(dto.version());
        breakpoint.setSusceptibleMax(dto.susceptibleMax());
        breakpoint.setResistantMin(dto.resistantMin());
        return breakpoint;
    }
}



