package pe.edu.utec.atlasrambackend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.dto.CreateFacilityDTO;
import pe.edu.utec.atlasrambackend.dto.FacilityResponseDTO;
import pe.edu.utec.atlasrambackend.exception.DuplicateResourceException;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.mapper.FacilityMapper;
import pe.edu.utec.atlasrambackend.model.District;
import pe.edu.utec.atlasrambackend.model.Facility;
import pe.edu.utec.atlasrambackend.repository.DistrictRepository;
import pe.edu.utec.atlasrambackend.repository.FacilityRepository;

import java.util.List;

@Service
public class FacilityService {

    private final FacilityRepository facilityRepository;
    private final DistrictRepository districtRepository;
    private final FacilityMapper facilityMapper;

    public FacilityService(FacilityRepository facilityRepository,
                           DistrictRepository districtRepository,
                           FacilityMapper facilityMapper) {
        this.facilityRepository = facilityRepository;
        this.districtRepository = districtRepository;
        this.facilityMapper = facilityMapper;
    }

    @Transactional(readOnly = true)
    public Page<FacilityResponseDTO> findAll(Pageable pageable) {
        return facilityRepository.findAll(pageable).map(facilityMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public FacilityResponseDTO findById(Long id) {
        return facilityMapper.toResponse(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<FacilityResponseDTO> findByDistrict(Long districtId) {
        return facilityRepository.findByDistrictId(districtId).stream()
                .map(facilityMapper::toResponse)
                .toList();
    }

    @Transactional
    public FacilityResponseDTO create(CreateFacilityDTO dto) {
        if (facilityRepository.existsByCode(dto.code())) {
            throw new DuplicateResourceException("un establecimiento", "el código", dto.code());
        }
        Facility saved = facilityRepository.save(
                facilityMapper.toEntity(dto, getDistrictOrThrow(dto.districtId())));
        return facilityMapper.toResponse(saved);
    }

    @Transactional
    public FacilityResponseDTO update(Long id, CreateFacilityDTO dto) {
        Facility facility = getOrThrow(id);
        if (!facility.getCode().equals(dto.code())
                && facilityRepository.existsByCode(dto.code())) {
            throw new DuplicateResourceException("un establecimiento", "el código", dto.code());
        }
        facility.setCode(dto.code());
        facility.setName(dto.name());
        facility.setDistrict(getDistrictOrThrow(dto.districtId()));
        return facilityMapper.toResponse(facility);
    }

    @Transactional
    public void delete(Long id) {
        facilityRepository.delete(getOrThrow(id));
    }

    private Facility getOrThrow(Long id) {
        return facilityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Establecimiento", id));
    }

    private District getDistrictOrThrow(Long districtId) {
        return districtRepository.findById(districtId)
                .orElseThrow(() -> new ResourceNotFoundException("Distrito", districtId));
    }
}


