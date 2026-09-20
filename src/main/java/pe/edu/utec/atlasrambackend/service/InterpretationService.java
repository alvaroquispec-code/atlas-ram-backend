package pe.edu.utec.atlasrambackend.service;

import org.springframework.stereotype.Service;
import pe.edu.utec.atlasrambackend.model.Antibiotic;
import pe.edu.utec.atlasrambackend.model.Breakpoint;
import pe.edu.utec.atlasrambackend.model.Interpretation;
import pe.edu.utec.atlasrambackend.model.Microorganism;
import pe.edu.utec.atlasrambackend.repository.BreakpointRepository;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class InterpretationService {

    private final BreakpointRepository breakpointRepository;

    public InterpretationService(BreakpointRepository breakpointRepository) {
        this.breakpointRepository = breakpointRepository;
    }

    public Optional<Interpretation> interpret(
            BigDecimal mic,
            Microorganism microorganism,
            Antibiotic antibiotic,
            String standard,
            String version) {

        if (mic == null) {
            return Optional.empty();
        }

        return breakpointRepository
                .findByMicroorganismIdAndAntibioticIdAndStandardAndVersion(
                        microorganism.getId(), antibiotic.getId(), standard, version)
                .map(bp -> classify(mic, bp));
    }

    private Interpretation classify(BigDecimal mic, Breakpoint bp) {
        if (bp.getSusceptibleMax() != null
                && mic.compareTo(bp.getSusceptibleMax()) <= 0) {
            return Interpretation.SUSCEPTIBLE;
        }
        if (bp.getResistantMin() != null
                && mic.compareTo(bp.getResistantMin()) >= 0) {
            return Interpretation.RESISTANT;
        }
        return Interpretation.INTERMEDIATE;
    }
}