package pe.edu.utec.atlasrambackend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.dto.AntibioticResponseDTO;
import pe.edu.utec.atlasrambackend.dto.CreateAntibioticDTO;
import pe.edu.utec.atlasrambackend.exception.DuplicateResourceException;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.mapper.AntibioticMapper;
import pe.edu.utec.atlasrambackend.model.Antibiotic;
import pe.edu.utec.atlasrambackend.repository.AntibioticRepository;

@Service
public class AntibioticService {

    private final AntibioticRepository antibioticRepository;
    private final AntibioticMapper antibioticMapper;

    public AntibioticService(AntibioticRepository antibioticRepository,
                             AntibioticMapper antibioticMapper) {
        this.antibioticRepository = antibioticRepository;
        this.antibioticMapper = antibioticMapper;
    }

    @Transactional(readOnly = true)
    public Page<AntibioticResponseDTO> findAll(Pageable pageable) {
        return antibioticRepository.findAll(pageable).map(antibioticMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public AntibioticResponseDTO findById(Long id) {
        return antibioticMapper.toResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public AntibioticResponseDTO findByCode(String code) {
        Antibiotic antibiotic = antibioticRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Antibiótico", code));
        return antibioticMapper.toResponse(antibiotic);
    }

    @Transactional
    public AntibioticResponseDTO create(CreateAntibioticDTO dto) {
        if (antibioticRepository.existsByCode(dto.code())) {
            throw new DuplicateResourceException("un antibiótico", "el código", dto.code());
        }
        Antibiotic saved = antibioticRepository.save(antibioticMapper.toEntity(dto));
        return antibioticMapper.toResponse(saved);
    }

    @Transactional
    public AntibioticResponseDTO update(Long id, CreateAntibioticDTO dto) {
        Antibiotic antibiotic = getOrThrow(id);
        if (!antibiotic.getCode().equals(dto.code())
                && antibioticRepository.existsByCode(dto.code())) {
            throw new DuplicateResourceException("un antibiótico", "el código", dto.code());
        }
        antibiotic.setCode(dto.code());
        antibiotic.setName(dto.name());
        antibiotic.setAntibioticClass(dto.antibioticClass());
        return antibioticMapper.toResponse(antibiotic);
    }

    @Transactional
    public void delete(Long id) {
        antibioticRepository.delete(getOrThrow(id));
    }

    private Antibiotic getOrThrow(Long id) {
        return antibioticRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Antibiótico", id));
    }
}

