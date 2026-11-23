package pe.edu.utec.atlasrambackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.edu.utec.atlasrambackend.dto.StockConsumptionDTO;
import pe.edu.utec.atlasrambackend.dto.StockMovementResponseDTO;
import pe.edu.utec.atlasrambackend.dto.StockReceiptDTO;
import pe.edu.utec.atlasrambackend.dto.StockWriteOffDTO;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.mapper.StockMapper;
import pe.edu.utec.atlasrambackend.model.MovementType;
import pe.edu.utec.atlasrambackend.model.StockItem;
import pe.edu.utec.atlasrambackend.model.StockLot;
import pe.edu.utec.atlasrambackend.model.StockMovement;
import pe.edu.utec.atlasrambackend.repository.AntibioticRepository;
import pe.edu.utec.atlasrambackend.repository.FacilityRepository;
import pe.edu.utec.atlasrambackend.repository.StockItemRepository;
import pe.edu.utec.atlasrambackend.repository.StockLotRepository;
import pe.edu.utec.atlasrambackend.repository.StockMovementRepository;
import pe.edu.utec.atlasrambackend.repository.UserRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockServiceTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Lima"));

    private StockItemRepository itemRepository;
    private StockLotRepository lotRepository;
    private StockMovementRepository movementRepository;
    private StockService service;
    private StockItem item;

    @BeforeEach
    void setUp() {
        itemRepository = mock(StockItemRepository.class);
        lotRepository = mock(StockLotRepository.class);
        movementRepository = mock(StockMovementRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new StockService(itemRepository, lotRepository, movementRepository,
                mock(FacilityRepository.class), mock(AntibioticRepository.class), userRepository, new StockMapper());

        item = new StockItem();
        item.setId(1L);
        item.setQuantityOnOrder(100);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
    }

    private StockLot lot(long id, String number, LocalDate expiry, int onHand) {
        StockLot lot = new StockLot();
        lot.setId(id);
        lot.setStockItem(item);
        lot.setLotNumber(number);
        lot.setExpiryDate(expiry);
        lot.setQuantityReceived(onHand);
        lot.setQuantityOnHand(onHand);
        lot.setReceivedOn(TODAY.minusDays(30));
        return lot;
    }

    @Test
    void elConsumoSaleDelLoteQueVenceAntes() {
        StockLot first = lot(10L, "A", TODAY.plusDays(20), 5);
        StockLot second = lot(11L, "B", TODAY.plusDays(200), 10);
        when(lotRepository.findUsableForUpdate(1L, TODAY)).thenReturn(List.of(first, second));

        List<StockMovementResponseDTO> movements =
                service.consume(1L, new StockConsumptionDTO(8, TODAY, null), "farmacia@atlasram.test");

        assertThat(first.getQuantityOnHand()).isZero();
        assertThat(second.getQuantityOnHand()).isEqualTo(7);
        assertThat(movements).extracting(StockMovementResponseDTO::lotNumber).containsExactly("A", "B");
        assertThat(movements).extracting(StockMovementResponseDTO::quantity).containsExactly(5, 3);
        assertThat(movements).allMatch(m -> m.type() == MovementType.CONSUMPTION);
    }

    @Test
    void unConsumoMayorQueElStockSeRechazaCompleto() {
        StockLot only = lot(10L, "A", TODAY.plusDays(20), 5);
        when(lotRepository.findUsableForUpdate(1L, TODAY)).thenReturn(List.of(only));

        assertThatThrownBy(() -> service.consume(1L, new StockConsumptionDTO(6, TODAY, null), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Stock insuficiente");
        assertThat(only.getQuantityOnHand()).isEqualTo(5);
        verify(movementRepository, never()).save(any());
    }

    @Test
    void noSeRegistraUnConsumoFuturo() {
        assertThatThrownBy(() -> service.consume(1L, new StockConsumptionDTO(1, TODAY.plusDays(1), null), null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void elIngresoDescuentaLoPendienteDeLlegar() {
        when(lotRepository.existsByStockItemIdAndLotNumberIgnoreCase(1L, "L-01")).thenReturn(false);

        StockMovementResponseDTO movement = service.receive(1L,
                new StockReceiptDTO(" L-01 ", TODAY.plusYears(2), 60, TODAY), null);

        assertThat(movement.type()).isEqualTo(MovementType.RECEIPT);
        assertThat(movement.lotNumber()).isEqualTo("L-01");
        assertThat(item.getQuantityOnOrder()).isEqualTo(40);
    }

    @Test
    void noSeRecibeUnLoteVencido() {
        assertThatThrownBy(() -> service.receive(1L,
                new StockReceiptDTO("L-02", TODAY, 10, TODAY), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("vencido");
    }

    @Test
    void laBajaNoPuedeSuperarElSaldo() {
        StockLot expired = lot(10L, "A", TODAY.minusDays(1), 5);
        when(lotRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.writeOff(10L, new StockWriteOffDTO(6, TODAY, "Vencido"), null))
                .isInstanceOf(BusinessRuleException.class);

        StockMovementResponseDTO movement = service.writeOff(10L, new StockWriteOffDTO(null, TODAY, "Vencido"), null);
        assertThat(movement.type()).isEqualTo(MovementType.WRITE_OFF);
        assertThat(movement.quantity()).isEqualTo(5);
        assertThat(expired.getQuantityOnHand()).isZero();
    }
}
