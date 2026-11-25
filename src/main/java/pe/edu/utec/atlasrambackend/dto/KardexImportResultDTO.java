package pe.edu.utec.atlasrambackend.dto;

import java.util.List;

/** Resultado de importar un kardex: cada fila se aplica sola; las que fallan se informan con su línea. */
public record KardexImportResultDTO(
        int totalRows,
        int processedRows,
        int failedRows,
        int receipts,
        int consumptions,
        int writeOffs,
        List<String> errors
) {}
