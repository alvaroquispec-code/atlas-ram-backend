package pe.edu.utec.atlasrambackend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.dto.CreateDistrictDTO;
import pe.edu.utec.atlasrambackend.dto.DistrictResponseDTO;
import pe.edu.utec.atlasrambackend.exception.DuplicateResourceException;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.mapper.DistrictMapper;
import pe.edu.utec.atlasrambackend.model.District;
import pe.edu.utec.atlasrambackend.repository.DistrictRepository;

@Service
public class DistrictService {

    private final DistrictRepository districtRepository;
    private final DistrictMapper districtMapper;

    public DistrictService(DistrictRepository districtRepository, DistrictMapper districtMapper) {
        this.districtRepository = districtRepository;
        this.districtMapper = districtMapper;
    }

    @Transactional(readOnly = true)
    public Page<DistrictResponseDTO> findAll(Pageable pageable) {
        return districtRepository.findAll(pageable).map(districtMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public DistrictResponseDTO findById(Long id) {
        return districtMapper.toResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public DistrictResponseDTO findByUbigeo(String ubigeo) {
        District district = districtRepository.findByUbigeo(ubigeo)
                .orElseThrow(() -> new ResourceNotFoundException("Distrito", ubigeo));
        return districtMapper.toResponse(district);
    }

    @Transactional
    public DistrictResponseDTO create(CreateDistrictDTO dto) {
        if (districtRepository.existsByUbigeo(dto.ubigeo())) {
            throw new DuplicateResourceException("un distrito", "el ubigeo", dto.ubigeo());
        }
        District saved = districtRepository.save(districtMapper.toEntity(dto));
        return districtMapper.toResponse(saved);
    }

    @Transactional
    public DistrictResponseDTO update(Long id, CreateDistrictDTO dto) {
        District district = getOrThrow(id);
        if (!district.getUbigeo().equals(dto.ubigeo())
                && districtRepository.existsByUbigeo(dto.ubigeo())) {
            throw new DuplicateResourceException("un distrito", "el ubigeo", dto.ubigeo());
        }
        district.setUbigeo(dto.ubigeo());
        district.setName(dto.name());
        district.setProvince(dto.province());
        district.setDepartment(dto.department());
        return districtMapper.toResponse(district);
    }

    @Transactional
    public void delete(Long id) {
        districtRepository.delete(getOrThrow(id));
    }

    private District getOrThrow(Long id) {
        return districtRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Distrito", id));
    }
}

