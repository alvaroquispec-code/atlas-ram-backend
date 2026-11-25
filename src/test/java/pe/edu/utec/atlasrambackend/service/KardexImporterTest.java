package pe.edu.utec.atlasrambackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import pe.edu.utec.atlasrambackend.dto.KardexImportResultDTO;
import pe.edu.utec.atlasrambackend.dto.StockConsumptionDTO;
import pe.edu.utec.atlasrambackend.dto.StockReceiptDTO;
import pe.edu.utec.atlasrambackend.dto.StockWriteOffDTO;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.exception.InvalidCsvFormatException;
import pe.edu.utec.atlasrambackend.model.Antibiotic;
import pe.edu.utec.atlasrambackend.model.StockItem;
import pe.edu.utec.atlasrambackend.model.StockLot;
import pe.edu.utec.atlasrambackend.repository.StockItemRepository;
import pe.edu.utec.atlasrambackend.repository.StockLotRepository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KardexImporterTest {

    private StockService stockService;
    private StockLotRepository lotRepository;
    private KardexImporter importer;

    private static StockItem item(long id, String code, String presentation) {
        Antibiotic antibiotic = new Antibiotic();
        antibiotic.setCode(code);
        StockItem item = new StockItem();
        item.setId(id);
        item.setAntibiotic(antibiotic);
        item.setPresentation(presentation);
        return item;
    }

    @BeforeEach
    void setUp() {
        stockService = mock(StockService.class);
        StockItemRepository itemRepository = mock(StockItemRepository.class);
        lotRepository = mock(StockLotRepository.class);
        when(itemRepository.findByFacilityIdAndActiveTrueOrderByIdAsc(6L)).thenReturn(List.of(
                item(1L, "AMK", "Ampolla 500 mg"),
                item(2L, "CRO", "Vial 1 g"), item(3L, "CRO", "Vial 2 g")));
        importer = new KardexImporter(stockService, itemRepository, lotRepository);
    }

    private KardexImportResultDTO run(String csv) {
        return importer.importFile(6L, csv.getBytes(StandardCharsets.UTF_8), "farmacia@test");
    }

    @Test
    void elMismoDiaPrimeroLosIngresosAunqueVenganDespues() {
        KardexImportResultDTO r = run("""
                Fecha;Tipo de movimiento;Antibiótico;Cantidad;Lote;Fecha de vencimiento;Observación
                05/10/2026;CONSUMO;amk;40;;;UCI
                05/10/2026;INGRESO;AMK;100;L-1;31/12/2027;
                """);

        assertThat(r.processedRows()).isEqualTo(2);
        assertThat(r.receipts()).isEqualTo(1);
        assertThat(r.consumptions()).isEqualTo(1);
        InOrder order = inOrder(stockService);
        order.verify(stockService).receive(eq(1L),
                eq(new StockReceiptDTO("L-1", LocalDate.of(2027, 12, 31), 100, LocalDate.of(2026, 10, 5))),
                eq("farmacia@test"));
        order.verify(stockService).consume(eq(1L),
                eq(new StockConsumptionDTO(40, LocalDate.of(2026, 10, 5), "UCI")), eq("farmacia@test"));
    }

    @Test
    void lasFilasConErrorSeInformanYLasDemasSeAplican() {
        when(stockService.consume(eq(1L), any(), anyString()))
                .thenThrow(new BusinessRuleException("Stock insuficiente: se pidieron 999 unidades"));

        KardexImportResultDTO r = run("""
                fecha,tipo,codigo_antibiotico,cantidad,lote,vencimiento
                2026-10-01,TRASLADO,AMK,5,,
                2026-10-01,CONSUMO,AMK,999,,
                2026-10-01,CONSUMO,VAN,5,,
                2026-10-01,CONSUMO,CRO,5,,
                2026-10-01,INGRESO,AMK,10,,2027-01-01
                2026-10-01,CONSUMO,AMK,12.5,,
                """);

        assertThat(r.totalRows()).isEqualTo(6);
        assertThat(r.processedRows()).isZero();
        assertThat(r.failedRows()).isEqualTo(6);
        assertThat(r.errors()).hasSize(6);
        assertThat(r.errors().get(0)).startsWith("Línea 2").contains("TRASLADO");
        assertThat(r.errors().get(1)).startsWith("Línea 3").contains("Stock insuficiente");
        assertThat(r.errors().get(2)).startsWith("Línea 4").contains("no tiene un ítem activo de VAN");
        assertThat(r.errors().get(3)).startsWith("Línea 5").contains("varias presentaciones");
        assertThat(r.errors().get(4)).startsWith("Línea 6").contains("lote");
        assertThat(r.errors().get(5)).startsWith("Línea 7").contains("cantidad inválida");
    }

    @Test
    void laPresentacionDesambiguaYLaBajaBuscaElLote() {
        StockLot lot = new StockLot();
        lot.setId(77L);
        when(lotRepository.findByStockItemIdAndLotNumberIgnoreCase(3L, "C-9")).thenReturn(Optional.of(lot));

        KardexImportResultDTO r = run("""
                fecha,tipo,codigo_antibiotico,presentacion,cantidad,lote,motivo
                2026-10-02,BAJA,CRO,vial 2 g,3,C-9,Vencido
                """);

        assertThat(r.writeOffs()).isEqualTo(1);
        verify(stockService).writeOff(eq(77L),
                eq(new StockWriteOffDTO(3, LocalDate.of(2026, 10, 2), "Vencido")), eq("farmacia@test"));
    }

    @Test
    void sinLasColumnasMinimasSeRechazaElArchivo() {
        assertThatThrownBy(() -> run("""
                fecha,codigo_antibiotico,cantidad
                2026-10-01,AMK,5
                """))
                .isInstanceOf(InvalidCsvFormatException.class)
                .hasMessageContaining("tipo");
        verify(stockService, never()).consume(any(), any(), any());
    }

    @Test
    void cantidadEsUnEnteroDeUnidades() {
        assertThat(KardexImporter.quantity("120")).isEqualTo(120);
        assertThat(KardexImporter.quantity("120.0")).isEqualTo(120);
        assertThatThrownBy(() -> KardexImporter.quantity("12.5")).hasMessageContaining("cantidad inválida");
        assertThatThrownBy(() -> KardexImporter.quantity("doce")).hasMessageContaining("cantidad inválida");
    }
}
