package pe.edu.utec.atlasrambackend.dto;

import java.util.List;

/**
 * Resultado de reinterpretar las CIM y halos guardados con otra edición de puntos de corte.
 *
 * @param applied           false = vista previa (no se cambió nada); true = se guardó
 * @param evaluated         resultados con CIM o halo que se revisaron
 * @param changed           resultados cuya categoría S/I/R cambia
 * @param withoutBreakpoint resultados sin punto de corte en esa edición (se dejan como estaban)
 * @param changes           cambios agrupados por germen, antibiótico y categoría, de más a menos frecuente
 */
public record ReinterpretationReportDTO(
        String standard,
        String version,
        boolean applied,
        int evaluated,
        int changed,
        int withoutBreakpoint,
        List<Change> changes
) {
    public record Change(String microorganism, String antibiotic, String from, String to, int count) {}
}
