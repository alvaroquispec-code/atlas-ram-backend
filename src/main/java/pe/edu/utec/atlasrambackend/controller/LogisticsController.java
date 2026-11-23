package pe.edu.utec.atlasrambackend.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utec.atlasrambackend.dto.CreateStockItemDTO;
import pe.edu.utec.atlasrambackend.dto.DddReportDTO;
import pe.edu.utec.atlasrambackend.dto.FacilityStockReportDTO;
import pe.edu.utec.atlasrambackend.dto.StockConsumptionDTO;
import pe.edu.utec.atlasrambackend.dto.StockItemResponseDTO;
import pe.edu.utec.atlasrambackend.dto.StockLotResponseDTO;
import pe.edu.utec.atlasrambackend.dto.StockMovementResponseDTO;
import pe.edu.utec.atlasrambackend.dto.StockReceiptDTO;
import pe.edu.utec.atlasrambackend.dto.StockWriteOffDTO;
import pe.edu.utec.atlasrambackend.dto.UpdateStockItemDTO;
import pe.edu.utec.atlasrambackend.service.LogisticsService;
import pe.edu.utec.atlasrambackend.service.StockService;

import java.time.LocalDate;
import java.util.List;

/**
 * Inventario de antibióticos. Lectura: farmacéutico (su establecimiento), epidemiólogo y administrador.
 * Escritura: farmacéutico (su establecimiento) y administrador.
 */
@RestController
@RequestMapping("/logistics")
public class LogisticsController {

    private final StockService stockService;
    private final LogisticsService logisticsService;

    public LogisticsController(StockService stockService, LogisticsService logisticsService) {
        this.stockService = stockService;
        this.logisticsService = logisticsService;
    }

    /** Tablero del establecimiento: estado SISMED, pedido sugerido, vencimientos y presión de brote por ítem. */
    @GetMapping("/facilities/{facilityId}/stock")
    @PreAuthorize("@facilityAccess.canAccess(authentication, #facilityId)")
    public ResponseEntity<FacilityStockReportDTO> report(@PathVariable Long facilityId) {
        return ResponseEntity.ok(logisticsService.report(facilityId));
    }

    /**
     * Consumo en DDD por 100 camas-día, por grupo AWaRe.
     * Ejemplo: /logistics/facilities/6/ddd?from=2026-01-01&to=2026-06-30 (bedDays opcional).
     */
    @GetMapping("/facilities/{facilityId}/ddd")
    @PreAuthorize("@facilityAccess.canAccess(authentication, #facilityId)")
    public ResponseEntity<DddReportDTO> ddd(
            @PathVariable Long facilityId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Double bedDays) {
        return ResponseEntity.ok(logisticsService.ddd(facilityId, from, to, bedDays));
    }

    @PostMapping("/items")
    @PreAuthorize("@facilityAccess.canAccess(authentication, #dto.facilityId())")
    public ResponseEntity<StockItemResponseDTO> createItem(@Valid @RequestBody CreateStockItemDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stockService.createItem(dto));
    }

    @GetMapping("/items/{itemId}")
    @PreAuthorize("@facilityAccess.canAccessStockItem(authentication, #itemId)")
    public ResponseEntity<StockItemResponseDTO> findItem(@PathVariable Long itemId) {
        return ResponseEntity.ok(stockService.findItem(itemId));
    }

    @PutMapping("/items/{itemId}")
    @PreAuthorize("@facilityAccess.canAccessStockItem(authentication, #itemId)")
    public ResponseEntity<StockItemResponseDTO> updateItem(@PathVariable Long itemId,
                                                           @Valid @RequestBody UpdateStockItemDTO dto) {
        return ResponseEntity.ok(stockService.updateItem(itemId, dto));
    }

    @GetMapping("/items/{itemId}/lots")
    @PreAuthorize("@facilityAccess.canAccessStockItem(authentication, #itemId)")
    public ResponseEntity<List<StockLotResponseDTO>> lots(@PathVariable Long itemId) {
        return ResponseEntity.ok(logisticsService.lots(itemId));
    }

    /** Kardex del ítem, del movimiento más reciente al más antiguo. */
    @GetMapping("/items/{itemId}/movements")
    @PreAuthorize("@facilityAccess.canAccessStockItem(authentication, #itemId)")
    public ResponseEntity<Page<StockMovementResponseDTO>> movements(@PathVariable Long itemId,
                                                                    @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(stockService.movements(itemId, pageable));
    }

    @PostMapping("/items/{itemId}/receipts")
    @PreAuthorize("@facilityAccess.canAccessStockItem(authentication, #itemId)")
    public ResponseEntity<StockMovementResponseDTO> receive(@PathVariable Long itemId,
                                                            @Valid @RequestBody StockReceiptDTO dto,
                                                            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(stockService.receive(itemId, dto, authentication.getName()));
    }

    @PostMapping("/items/{itemId}/consumptions")
    @PreAuthorize("@facilityAccess.canAccessStockItem(authentication, #itemId)")
    public ResponseEntity<List<StockMovementResponseDTO>> consume(@PathVariable Long itemId,
                                                                  @Valid @RequestBody StockConsumptionDTO dto,
                                                                  Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(stockService.consume(itemId, dto, authentication.getName()));
    }

    @PostMapping("/lots/{lotId}/write-off")
    @PreAuthorize("@facilityAccess.canAccessStockLot(authentication, #lotId)")
    public ResponseEntity<StockMovementResponseDTO> writeOff(@PathVariable Long lotId,
                                                             @Valid @RequestBody StockWriteOffDTO dto,
                                                             Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(stockService.writeOff(lotId, dto, authentication.getName()));
    }
}
