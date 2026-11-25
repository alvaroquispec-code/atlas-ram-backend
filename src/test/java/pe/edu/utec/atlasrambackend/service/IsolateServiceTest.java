package pe.edu.utec.atlasrambackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pe.edu.utec.atlasrambackend.dto.CreateIsolateDTO;
import pe.edu.utec.atlasrambackend.dto.CreateIsolateResultDTO;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.mapper.IsolateMapper;
import pe.edu.utec.atlasrambackend.model.Antibiotic;
import pe.edu.utec.atlasrambackend.model.Facility;
import pe.edu.utec.atlasrambackend.model.Interpretation;
import pe.edu.utec.atlasrambackend.model.InterpretationSource;
import pe.edu.utec.atlasrambackend.model.Isolate;
import pe.edu.utec.atlasrambackend.model.MicQualifier;
import pe.edu.utec.atlasrambackend.model.Microorganism;
import pe.edu.utec.atlasrambackend.model.Specimen;
import pe.edu.utec.atlasrambackend.model.Ward;
import pe.edu.utec.atlasrambackend.repository.AntibioticRepository;
import pe.edu.utec.atlasrambackend.repository.DistrictRepository;
import pe.edu.utec.atlasrambackend.repository.FacilityRepository;
import pe.edu.utec.atlasrambackend.repository.IsolateRepository;
import pe.edu.utec.atlasrambackend.repository.MicroorganismRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IsolateServiceTest {

    private IsolateRepository isolateRepository;
    private InterpretationService interpretationService;
    private IsolateService service;

    @BeforeEach
    void setUp() {
        isolateRepository = mock(IsolateRepository.class);
        FacilityRepository facilityRepository = mock(FacilityRepository.class);
        MicroorganismRepository microorganismRepository = mock(MicroorganismRepository.class);
        AntibioticRepository antibioticRepository = mock(AntibioticRepository.class);
        interpretationService = mock(InterpretationService.class);
        service = new IsolateService(isolateRepository, facilityRepository, mock(DistrictRepository.class),
                microorganismRepository, mock(IsolateMapper.class), antibioticRepository, interpretationService);

        when(facilityRepository.findById(1L)).thenReturn(Optional.of(new Facility()));
        when(microorganismRepository.findById(2L)).thenReturn(Optional.of(new Microorganism()));
        Antibiotic meropenem = new Antibiotic();
        meropenem.setName("Meropenem");
        Antibiotic amikacina = new Antibiotic();
        amikacina.setName("Amikacina");
        when(antibioticRepository.findById(11L)).thenReturn(Optional.of(meropenem));
        when(antibioticRepository.findById(12L)).thenReturn(Optional.of(amikacina));
        when(interpretationService.resolve(any(), any(), any(), any(), any(), any())).thenReturn(Optional.of(
                new InterpretationService.Resolution(Interpretation.RESISTANT, InterpretationSource.CALCULATED,
                        "CLSI", "2024")));
        when(isolateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static CreateIsolateDTO dto(List<CreateIsolateResultDTO> results) {
        return new CreateIsolateDTO(LocalDate.of(2026, 11, 20), Specimen.BLOOD, 63, "M", 1L, null, 2L, null,
                Ward.ICU, results);
    }

    @Test
    void elAislamientoSeGuardaConSuAntibiogramaEnUnaSolaOperacion() {
        service.create(dto(List.of(new CreateIsolateResultDTO(11L, ">=16", null, null),
                new CreateIsolateResultDTO(12L, "4", null, null))));

        ArgumentCaptor<Isolate> saved = ArgumentCaptor.forClass(Isolate.class);
        verify(isolateRepository).save(saved.capture());
        assertThat(saved.getValue().getWard()).isEqualTo(Ward.ICU);
        assertThat(saved.getValue().getResults()).hasSize(2);
        var mem = saved.getValue().getResults().get(0);
        assertThat(mem.getMicQualifier()).isEqualTo(MicQualifier.GE);
        assertThat(mem.getMicValue()).isEqualByComparingTo(new BigDecimal("16"));
        assertThat(mem.getInterpretation()).isEqualTo(Interpretation.RESISTANT);
    }

    @Test
    void unaCimIlegibleNoDejaUnAislamientoSinAntibiograma() {
        assertThatThrownBy(() -> service.create(dto(List.of(new CreateIsolateResultDTO(11L, "dieciséis", null, null)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Meropenem");
        verify(isolateRepository, never()).save(any());
    }

    @Test
    void unAntibioticoRepetidoSeRechaza() {
        assertThatThrownBy(() -> service.create(dto(List.of(new CreateIsolateResultDTO(11L, "1", null, null),
                new CreateIsolateResultDTO(11L, "2", null, null)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("dos veces");
    }
}
