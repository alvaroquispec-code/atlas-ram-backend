package pe.edu.utec.atlasrambackend.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Coordinación de estados de una carga en segundo plano. */
class UploadServiceTest {

    private final UploadImporter importer = mock(UploadImporter.class);
    private final UploadStatusWriter statusWriter = mock(UploadStatusWriter.class);
    private final UploadService service = new UploadService(importer, statusWriter);
    private final CsvParser.Result parsed = new CsvParser.Result(List.of(), List.of());

    @Test
    void unaCargaCorrectaPasaPorProcessingYNoSeMarcaFallida() {
        service.process(7L, parsed);

        var order = inOrder(statusWriter, importer);
        order.verify(statusWriter).markProcessing(7L);
        order.verify(importer).importRows(7L, parsed);
        verify(statusWriter, never()).markFailed(any(), any());
    }

    @Test
    void unErrorDeBaseDeDatosDejaLaCargaFailedConLaCausaReal() {
        // Antes: el FAILED se guardaba en la transacción abortada, se perdía y la carga quedaba en PENDING.
        doThrow(new DataIntegrityViolationException("could not execute statement",
                new SQLException("duplicate key value violates unique constraint \"uk_isolate_lab_id\"")))
                .when(importer).importRows(eq(7L), any());

        service.process(7L, parsed);

        verify(statusWriter).markFailed(eq(7L), contains("uk_isolate_lab_id"));
    }

    @Test
    void elMensajeDeFalloExplicaQueNoSeGuardoNada() {
        assertThat(UploadService.failureMessage(new IllegalStateException("x", new RuntimeException("causa"))))
                .isEqualTo("La carga se interrumpió y no se guardó ninguna fila: causa");
    }
}
