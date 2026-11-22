package pe.edu.utec.atlasrambackend.interpretation;

import pe.edu.utec.atlasrambackend.model.MicQualifier;

import java.math.BigDecimal;

/** CIM separada en calificador y valor: "<=0.25" se convierte en (LE, 0.25). */
public record ParsedMic(MicQualifier qualifier, BigDecimal value) {
}
