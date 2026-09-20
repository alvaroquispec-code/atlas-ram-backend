package pe.edu.utec.atlasrambackend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.dto.CreateMicroorganismDTO;
import pe.edu.utec.atlasrambackend.dto.MicroorganismResponseDTO;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.exception.DuplicateResourceException;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.mapper.MicroorganismMapper;
import pe.edu.utec.atlasrambackend.model.GramStain;
import pe.edu.utec.atlasrambackend.model.Microorganism;
import pe.edu.utec.atlasrambackend.repository.MicroorganismRepository;

import java.util.Arrays;

@Service
public class MicroorganismService {

    private final MicroorganismRepository microorganismRepository;
    private final MicroorganismMapper microorganismMapper;

    public MicroorganismService(MicroorganismRepository microorganismRepository,
                                MicroorganismMapper microorganismMapper) {
        this.microorganismRepository = microorganismRepository;
        this.microorganismMapper = microorganismMapper;
    }

    @Transactional(readOnly = true)
    public Page<MicroorganismResponseDTO> findAll(Pageable pageable) {
        return microorganismRepository.findAll(pageable).map(microorganismMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public MicroorganismResponseDTO findById(Long id) {
        return microorganismMapper.toResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public MicroorganismResponseDTO findByCode(String code) {
        Microorganism microorganism = microorganismRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Microorganismo", code));
        return microorganismMapper.toResponse(microorganism);
    }

    @Transactional
    public MicroorganismResponseDTO create(CreateMicroorganismDTO dto) {
        validateGramStain(dto.gramStain());
        if (microorganismRepository.existsByCode(dto.code())) {
            throw new DuplicateResourceException("un microorganismo", "el código", dto.code());
        }
        Microorganism saved = microorganismRepository.save(microorganismMapper.toEntity(dto));
        return microorganismMapper.toResponse(saved);
    }

    @Transactional
    public MicroorganismResponseDTO update(Long id, CreateMicroorganismDTO dto) {
        validateGramStain(dto.gramStain());
        Microorganism microorganism = getOrThrow(id);
        if (!microorganism.getCode().equals(dto.code())
                && microorganismRepository.existsByCode(dto.code())) {
            throw new DuplicateResourceException("un microorganismo", "el código", dto.code());
        }
        microorganism.setCode(dto.code());
        microorganism.setGenus(dto.genus());
        microorganism.setSpecies(dto.species());
        microorganism.setGramStain(dto.gramStain() == null ? null : GramStain.valueOf(dto.gramStain()));
        return microorganismMapper.toResponse(microorganism);
    }

    @Transactional
    public void delete(Long id) {
        microorganismRepository.delete(getOrThrow(id));
    }

    private void validateGramStain(String gramStain) {
        if (gramStain == null) {
            return;
        }
        boolean valid = Arrays.stream(GramStain.values())
                .anyMatch(value -> value.name().equals(gramStain));
        if (!valid) {
            throw new BusinessRuleException("Tinción de Gram inválida: " + gramStain
                    + ". Valores permitidos: " + Arrays.toString(GramStain.values()));
        }
    }

    private Microorganism getOrThrow(Long id) {
        return microorganismRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Microorganismo", id));
    }
}

