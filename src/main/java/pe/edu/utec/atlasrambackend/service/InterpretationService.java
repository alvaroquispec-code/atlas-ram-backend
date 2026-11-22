package pe.edu.utec.atlasrambackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pe.edu.utec.atlasrambackend.interpretation.ParsedMic;
import pe.edu.utec.atlasrambackend.model.Antibiotic;
import pe.edu.utec.atlasrambackend.model.Breakpoint;
import pe.edu.utec.atlasrambackend.model.Interpretation;
import pe.edu.utec.atlasrambackend.model.InterpretationSource;
import pe.edu.utec.atlasrambackend.model.Microorganism;
import pe.edu.utec.atlasrambackend.repository.BreakpointRepository;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Interpreta una CIM contra el punto de corte vigente y deja registrado de dónde salió la interpretación.
 *
 * Con calificador, la CIM real es un rango, no un número. Solo se asigna una categoría cuando todo el
 * rango cae dentro de ella; si el rango cruza categorías, no se adivina. Como las diluciones son dobles
 * (…, 0.5, 1, 2, 4…), "<x" equivale a "≤ x/2" y ">x" equivale a "≥ 2x".
 */
@Service
public class InterpretationService {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private final BreakpointRepository breakpointRepository;
    private final String standard;
    private final String version;

    public InterpretationService(BreakpointRepository breakpointRepository,
                                 @Value("${breakpoints.standard:CLSI}") String standard,
                                 @Value("${breakpoints.version:2024}") String version) {
        this.breakpointRepository = breakpointRepository;
        this.standard = standard;
        this.version = version;
    }

    /** Interpretación final de un resultado, con su procedencia. Estándar y versión son null si la informó el laboratorio. */
    public record Resolution(Interpretation interpretation, InterpretationSource source,
                             String breakpointStandard, String breakpointVersion) {
    }

    /**
     * Calcula la interpretación si hay CIM y punto de corte y el rango es concluyente. Si no, usa la del
     * laboratorio, marcada como LAB_REPORTED. Vacío si no hay ninguna de las dos.
     */
    public Optional<Resolution> resolve(ParsedMic mic, Interpretation reported,
                                        Microorganism microorganism, Antibiotic antibiotic) {
        Optional<Interpretation> calculated = Optional.ofNullable(mic).flatMap(m -> breakpointRepository
                .findByMicroorganismIdAndAntibioticIdAndStandardAndVersion(
                        microorganism.getId(), antibiotic.getId(), standard, version)
                .flatMap(bp -> classify(m, bp)));
        if (calculated.isPresent()) {
            return Optional.of(new Resolution(calculated.get(), InterpretationSource.CALCULATED, standard, version));
        }
        return Optional.ofNullable(reported)
                .map(r -> new Resolution(r, InterpretationSource.LAB_REPORTED, null, null));
    }

    /** Categoría que contiene todo el rango de la CIM; vacío si el rango cruza categorías. */
    static Optional<Interpretation> classify(ParsedMic mic, Breakpoint bp) {
        BigDecimal value = mic.value();
        return switch (mic.qualifier()) {
            case EQ -> Optional.of(classifyExact(value, bp));
            case LE -> susceptibleIfAtMost(value, bp);
            case LT -> susceptibleIfAtMost(value.divide(TWO), bp);
            case GE -> resistantIfAtLeast(value, bp);
            case GT -> resistantIfAtLeast(value.multiply(TWO), bp);
        };
    }

    private static Interpretation classifyExact(BigDecimal mic, Breakpoint bp) {
        if (bp.getSusceptibleMax() != null && mic.compareTo(bp.getSusceptibleMax()) <= 0) {
            return Interpretation.SUSCEPTIBLE;
        }
        if (bp.getResistantMin() != null && mic.compareTo(bp.getResistantMin()) >= 0) {
            return Interpretation.RESISTANT;
        }
        return Interpretation.INTERMEDIATE;
    }

    /** La CIM real es como máximo {@code upper}: es sensible solo si ese máximo ya es sensible. */
    private static Optional<Interpretation> susceptibleIfAtMost(BigDecimal upper, Breakpoint bp) {
        return bp.getSusceptibleMax() != null && upper.compareTo(bp.getSusceptibleMax()) <= 0
                ? Optional.of(Interpretation.SUSCEPTIBLE)
                : Optional.empty();
    }

    /** La CIM real es como mínimo {@code lower}: es resistente solo si ese mínimo ya es resistente. */
    private static Optional<Interpretation> resistantIfAtLeast(BigDecimal lower, Breakpoint bp) {
        return bp.getResistantMin() != null && lower.compareTo(bp.getResistantMin()) >= 0
                ? Optional.of(Interpretation.RESISTANT)
                : Optional.empty();
    }
}
