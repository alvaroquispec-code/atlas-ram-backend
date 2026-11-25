package pe.edu.utec.atlasrambackend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.dto.CreateStockItemDTO;
import pe.edu.utec.atlasrambackend.dto.StockConsumptionDTO;
import pe.edu.utec.atlasrambackend.dto.StockItemResponseDTO;
import pe.edu.utec.atlasrambackend.dto.StockMovementResponseDTO;
import pe.edu.utec.atlasrambackend.dto.StockReceiptDTO;
import pe.edu.utec.atlasrambackend.dto.StockWriteOffDTO;
import pe.edu.utec.atlasrambackend.dto.UpdateStockItemDTO;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.exception.DuplicateResourceException;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.mapper.StockMapper;
import pe.edu.utec.atlasrambackend.model.Antibiotic;
import pe.edu.utec.atlasrambackend.model.Facility;
import pe.edu.utec.atlasrambackend.model.MovementType;
import pe.edu.utec.atlasrambackend.model.StockItem;
import pe.edu.utec.atlasrambackend.model.StockLot;
import pe.edu.utec.atlasrambackend.model.StockMovement;
import pe.edu.utec.atlasrambackend.model.User;
import pe.edu.utec.atlasrambackend.repository.AntibioticRepository;
import pe.edu.utec.atlasrambackend.repository.FacilityRepository;
import pe.edu.utec.atlasrambackend.repository.StockItemRepository;
import pe.edu.utec.atlasrambackend.repository.StockLotRepository;
import pe.edu.utec.atlasrambackend.repository.StockMovementRepository;
import pe.edu.utec.atlasrambackend.repository.UserRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Kardex de antibióticos: alta de ítems, ingresos por lote, consumos en orden FEFO y bajas.
 * El stock nunca queda negativo: un consumo mayor que el saldo utilizable se rechaza completo.
 */
@Service
public class StockService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final int DEFAULT_REVIEW_DAYS = 30;
    private static final int DEFAULT_LEAD_TIME_DAYS = 15;

    private final StockItemRepository itemRepository;
    private final StockLotRepository lotRepository;
    private final StockMovementRepository movementRepository;
    private final FacilityRepository facilityRepository;
    private final AntibioticRepository antibioticRepository;
    private final UserRepository userRepository;
    private final StockMapper mapper;

    public StockService(StockItemRepository itemRepository, StockLotRepository lotRepository,
                        StockMovementRepository movementRepository, FacilityRepository facilityRepository,
                        AntibioticRepository antibioticRepository, UserRepository userRepository,
                        StockMapper mapper) {
        this.itemRepository = itemRepository;
        this.lotRepository = lotRepository;
        this.movementRepository = movementRepository;
        this.facilityRepository = facilityRepository;
        this.antibioticRepository = antibioticRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    @Transactional
    public StockItemResponseDTO createItem(CreateStockItemDTO dto) {
        Facility facility = facilityRepository.findById(dto.facilityId())
                .orElseThrow(() -> new ResourceNotFoundException("Establecimiento", dto.facilityId()));
        Antibiotic antibiotic = antibioticRepository.findById(dto.antibioticId())
                .orElseThrow(() -> new ResourceNotFoundException("Antibiótico", dto.antibioticId()));
        String presentation = dto.presentation().trim();
        if (itemRepository.existsByFacilityIdAndAntibioticIdAndPresentationIgnoreCase(
                facility.getId(), antibiotic.getId(), presentation)) {
            throw new DuplicateResourceException("un ítem", "la presentación", presentation);
        }
        StockItem item = new StockItem();
        item.setFacility(facility);
        item.setAntibiotic(antibiotic);
        item.setPresentation(presentation);
        item.setGramsPerUnit(dto.gramsPerUnit());
        item.setDddGrams(dto.dddGrams());
        item.setUnitCost(dto.unitCost());
        item.setVenClass(dto.venClass());
        item.setReviewPeriodDays(dto.reviewPeriodDays() == null ? DEFAULT_REVIEW_DAYS : dto.reviewPeriodDays());
        item.setLeadTimeDays(dto.leadTimeDays() == null ? DEFAULT_LEAD_TIME_DAYS : dto.leadTimeDays());
        item.setServiceLevel(dto.serviceLevel());
        return mapper.toResponse(itemRepository.save(item));
    }

    @Transactional
    public StockItemResponseDTO updateItem(Long itemId, UpdateStockItemDTO dto) {
        StockItem item = getItem(itemId);
        item.setGramsPerUnit(dto.gramsPerUnit());
        item.setDddGrams(dto.dddGrams());
        item.setUnitCost(dto.unitCost());
        item.setVenClass(dto.venClass());
        item.setReviewPeriodDays(dto.reviewPeriodDays());
        item.setLeadTimeDays(dto.leadTimeDays());
        item.setServiceLevel(dto.serviceLevel());
        item.setQuantityOnOrder(dto.quantityOnOrder());
        item.setActive(dto.active());
        return mapper.toResponse(item);
    }

    @Transactional(readOnly = true)
    public StockItemResponseDTO findItem(Long itemId) {
        return mapper.toResponse(getItem(itemId));
    }

    @Transactional
    public StockMovementResponseDTO receive(Long itemId, StockReceiptDTO dto, String userEmail) {
        StockItem item = getItem(itemId);
        LocalDate today = LocalDate.now(LIMA);
        if (dto.receivedOn().isAfter(today)) {
            throw new BusinessRuleException("La fecha de ingreso no puede ser futura");
        }
        if (!dto.expiryDate().isAfter(dto.receivedOn())) {
            throw new BusinessRuleException("El lote ya está vencido en la fecha de ingreso");
        }
        String lotNumber = dto.lotNumber().trim();
        if (lotRepository.existsByStockItemIdAndLotNumberIgnoreCase(itemId, lotNumber)) {
            throw new DuplicateResourceException("un lote", "el número", lotNumber);
        }
        StockLot lot = new StockLot();
        lot.setStockItem(item);
        lot.setLotNumber(lotNumber);
        lot.setExpiryDate(dto.expiryDate());
        lot.setQuantityReceived(dto.quantity());
        lot.setQuantityOnHand(dto.quantity());
        lot.setReceivedOn(dto.receivedOn());
        lotRepository.save(lot);

        item.setQuantityOnOrder(Math.max(0, item.getQuantityOnOrder() - dto.quantity()));
        return mapper.toResponse(movementRepository.save(
                movement(item, lot, MovementType.RECEIPT, dto.quantity(), dto.receivedOn(), null, userEmail)));
    }

    /**
     * Descuenta el consumo de los lotes utilizables en la fecha, del que vence antes al que vence después.
     * Devuelve un movimiento por lote tocado.
     */
    @Transactional
    public List<StockMovementResponseDTO> consume(Long itemId, StockConsumptionDTO dto, String userEmail) {
        StockItem item = getItem(itemId);
        if (dto.consumedOn().isAfter(LocalDate.now(LIMA))) {
            throw new BusinessRuleException("La fecha de consumo no puede ser futura");
        }
        List<StockLot> lots = lotRepository.findUsableForUpdate(itemId, dto.consumedOn());
        int usable = lots.stream().mapToInt(StockLot::getQuantityOnHand).sum();
        if (usable < dto.quantity()) {
            throw new BusinessRuleException(String.format(
                    "Stock insuficiente: se pidieron %d unidades y hay %d utilizables (no vencidas) al %s",
                    dto.quantity(), usable, dto.consumedOn()));
        }
        int remaining = dto.quantity();
        List<StockMovementResponseDTO> result = new ArrayList<>();
        for (StockLot lot : lots) {
            if (remaining == 0) {
                break;
            }
            int taken = Math.min(remaining, lot.getQuantityOnHand());
            lot.setQuantityOnHand(lot.getQuantityOnHand() - taken);
            remaining -= taken;
            result.add(mapper.toResponse(movementRepository.save(
                    movement(item, lot, MovementType.CONSUMPTION, taken, dto.consumedOn(), dto.note(), userEmail))));
        }
        return result;
    }

    @Transactional
    public StockMovementResponseDTO writeOff(Long lotId, StockWriteOffDTO dto, String userEmail) {
        StockLot lot = lotRepository.findByIdForUpdate(lotId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote", lotId));
        if (dto.writtenOffOn().isAfter(LocalDate.now(LIMA))) {
            throw new BusinessRuleException("La fecha de baja no puede ser futura");
        }
        int quantity = dto.quantity() == null ? lot.getQuantityOnHand() : dto.quantity();
        if (quantity <= 0 || quantity > lot.getQuantityOnHand()) {
            throw new BusinessRuleException(String.format(
                    "El lote %s tiene %d unidades; no se pueden dar de baja %d",
                    lot.getLotNumber(), lot.getQuantityOnHand(), quantity));
        }
        lot.setQuantityOnHand(lot.getQuantityOnHand() - quantity);
        return mapper.toResponse(movementRepository.save(movement(
                lot.getStockItem(), lot, MovementType.WRITE_OFF, quantity, dto.writtenOffOn(),
                dto.reason().trim(), userEmail)));
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponseDTO> movements(Long itemId, Pageable pageable) {
        getItem(itemId);
        return movementRepository.findByStockItemIdOrderByMovementDateDescIdDesc(itemId, pageable)
                .map(mapper::toResponse);
    }

    private StockMovement movement(StockItem item, StockLot lot, MovementType type, int quantity,
                                   LocalDate date, String note, String userEmail) {
        StockMovement m = new StockMovement();
        m.setStockItem(item);
        m.setLot(lot);
        m.setType(type);
        m.setQuantity(quantity);
        m.setMovementDate(date);
        m.setNote(note == null || note.isBlank() ? null : note.trim());
        User user = userEmail == null ? null : userRepository.findByEmail(userEmail).orElse(null);
        m.setCreatedBy(user);
        return m;
    }

    private StockItem getItem(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Ítem de inventario", itemId));
    }
}
