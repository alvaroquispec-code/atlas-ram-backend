package pe.edu.utec.atlasrambackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Procesa una carga en segundo plano. No es transaccional a propósito: coordina tres transacciones
 * independientes para que el estado de la carga siempre quede bien registrado.
 *
 * <pre>
 *   markProcessing ──commit──▶ importRows ──commit──▶ COMPLETED
 *                                  │ excepción (rollback de toda la importación)
 *                                  ▼
 *                              markFailed ──commit──▶ FAILED + motivo
 * </pre>
 */
@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    private final UploadImporter importer;
    private final UploadStatusWriter statusWriter;

    public UploadService(UploadImporter importer, UploadStatusWriter statusWriter) {
        this.importer = importer;
        this.statusWriter = statusWriter;
    }

    @Async("uploadExecutor")
    public void process(Long uploadId, CsvParser.Result parsed) {
        statusWriter.markProcessing(uploadId);
        try {
            importer.importRows(uploadId, parsed);
        } catch (Exception e) {
            log.error("Carga {} falló: {}", uploadId, e.getMessage(), e);
            statusWriter.markFailed(uploadId, failureMessage(e));
        }
    }

    /** El mensaje de la causa raíz, que es la que explica el problema (no el envoltorio de Spring). */
    static String failureMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        return "La carga se interrumpió y no se guardó ninguna fila: " + message;
    }
}
