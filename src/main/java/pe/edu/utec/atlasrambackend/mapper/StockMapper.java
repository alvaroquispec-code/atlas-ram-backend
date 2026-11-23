package pe.edu.utec.atlasrambackend.mapper;

import org.springframework.stereotype.Component;
import pe.edu.utec.atlasrambackend.dto.StockItemResponseDTO;
import pe.edu.utec.atlasrambackend.dto.StockMovementResponseDTO;
import pe.edu.utec.atlasrambackend.model.StockItem;
import pe.edu.utec.atlasrambackend.model.StockMovement;

@Component
public class StockMapper {

    public StockItemResponseDTO toResponse(StockItem s) {
        return new StockItemResponseDTO(
                s.getId(),
                s.getFacility().getId(),
                s.getAntibiotic().getId(),
                s.getAntibiotic().getCode(),
                s.getAntibiotic().getName(),
                s.getAntibiotic().getAwareCategory(),
                s.getPresentation(),
                s.getGramsPerUnit(),
                s.getDddGrams(),
                s.getUnitCost(),
                s.getVenClass(),
                s.getReviewPeriodDays(),
                s.getLeadTimeDays(),
                s.getServiceLevel(),
                s.getQuantityOnOrder(),
                s.isActive());
    }

    public StockMovementResponseDTO toResponse(StockMovement m) {
        return new StockMovementResponseDTO(
                m.getId(),
                m.getStockItem().getId(),
                m.getLot() == null ? null : m.getLot().getId(),
                m.getLot() == null ? null : m.getLot().getLotNumber(),
                m.getType(),
                m.getQuantity(),
                m.getMovementDate(),
                m.getNote());
    }
}
