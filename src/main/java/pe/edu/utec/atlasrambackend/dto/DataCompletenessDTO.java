package pe.edu.utec.atlasrambackend.dto;

/**
 * Completitud de las variables del paciente que el INS exige para la vigilancia nacional (WHONET-INS:
 * "Variables mínimas obligatorias": sexo, edad, servicio y tipo de localización) entre los aislamientos de una
 * carga. Una carga de laboratorio se acepta aunque falten (rechazarla perdería el antibiograma), pero se
 * informa cuánto falta, como mide GLASS la calidad de los datos. Porcentajes de 0 a 100.
 */
public record DataCompletenessDTO(
        long isolates,
        double sexPercentage,
        double agePercentage,
        double wardPercentage
) {
    public static DataCompletenessDTO of(long isolates, long withSex, long withAge, long withWard) {
        return new DataCompletenessDTO(isolates, pct(withSex, isolates), pct(withAge, isolates), pct(withWard, isolates));
    }

    private static double pct(long part, long total) {
        return total == 0 ? 0 : part * 100.0 / total;
    }
}
