package pe.edu.utec.atlasrambackend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.dto.BreakpointResponseDTO;
import pe.edu.utec.atlasrambackend.dto.CreateBreakpointDTO;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.exception.DuplicateResourceException;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.mapper.BreakpointMapper;
import pe.edu.utec.atlasrambackend.model.Antibiotic;
import pe.edu.utec.atlasrambackend.model.Breakpoint;
import pe.edu.utec.atlasrambackend.model.Microorganism;
import pe.edu.utec.atlasrambackend.repository.AntibioticRepository;
import pe.edu.utec.atlasrambackend.repository.BreakpointRepository;
import pe.edu.utec.atlasrambackend.repository.MicroorganismRepository;

@Service
public class BreakpointService {

    private final BreakpointRepository breakpointRepository;
    private final MicroorganismRepository microorganismRepository;
    private final AntibioticRepository antibioticRepository;
    private final BreakpointMapper breakpointMapper;

    public BreakpointService(BreakpointRepository breakpointRepository,
                             MicroorganismRepository microorganismRepository,
                             AntibioticRepository antibioticRepository,
                             BreakpointMapper breakpointMapper) {
        this.breakpointRepository = breakpointRepository;
        this.microorganismRepository = microorganismRepository;
        this.antibioticRepository = antibioticRepository;
        this.breakpointMapper = breakpointMapper;
    }

    @Transactional(readOnly = true)
    public Page<BreakpointResponseDTO> findAll(Pageable pageable) {
        return breakpointRepository.findAll(pageable).map(breakpointMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public BreakpointResponseDTO findById(Long id) {
        return breakpointMapper.toResponse(getOrThrow(id));
    }

    @Transactional
    public BreakpointResponseDTO create(CreateBreakpointDTO dto) {
        validateThresholds(dto);
        if (breakpointRepository.existsByMicroorganismIdAndAntibioticIdAndStandardAndVersion(
                dto.microorganismId(), dto.antibioticId(), dto.standard(), dto.version())) {
            throw new DuplicateResourceException(
                    "Ya existe un punto de corte para esa combinación de microorganismo, "
                            + "antibiótico, norma y versión");
        }
        Breakpoint saved = breakpointRepository.save(breakpointMapper.toEntity(
                dto, getMicroorganismOrThrow(dto.microorganismId()),
                getAntibioticOrThrow(dto.antibioticId())));
        return breakpointMapper.toResponse(saved);
    }

    @Transactional
    public BreakpointResponseDTO update(Long id, CreateBreakpointDTO dto) {
        validateThresholds(dto);
        Breakpoint breakpoint = getOrThrow(id);
        breakpoint.setMicroorganism(getMicroorganismOrThrow(dto.microorganismId()));
        breakpoint.setAntibiotic(getAntibioticOrThrow(dto.antibioticId()));
        breakpoint.setStandard(dto.standard());
        breakpoint.setVersion(dto.version());
        breakpoint.setSusceptibleMax(dto.susceptibleMax());
        breakpoint.setResistantMin(dto.resistantMin());
        return breakpointMapper.toResponse(breakpoint);
    }

    @Transactional
    public void delete(Long id) {
        breakpointRepository.delete(getOrThrow(id));
    }

    private void validateThresholds(CreateBreakpointDTO dto) {
        if (dto.susceptibleMax() == null && dto.resistantMin() == null) {
            throw new BusinessRuleException(
                    "Un punto de corte necesita al menos un umbral: susceptibleMax o resistantMin");
        }
        if (dto.susceptibleMax() != null && dto.resistantMin() != null
                && dto.susceptibleMax().compareTo(dto.resistantMin()) > 0) {
            throw new BusinessRuleException(
                    "susceptibleMax no puede ser mayor que resistantMin");
        }
    }

    private Breakpoint getOrThrow(Long id) {
        return breakpointRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Punto de corte", id));
    }

    private Microorganism getMicroorganismOrThrow(Long id) {
        return microorganismRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Microorganismo", id));
    }

    private Antibiotic getAntibioticOrThrow(Long id) {
        return antibioticRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Antibiótico", id));
    }
}




