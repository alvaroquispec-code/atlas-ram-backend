package pe.edu.utec.atlasrambackend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    /**
     * Establecimientos donde el usuario puede registrar aislamientos: todos para quien
     * tiene alcance global (administrador o epidemiólogo), y solo los asignados para el resto.
     */
    @Transactional(readOnly = true)
    public List<FacilityResponseDTO> findAccessible(String email, boolean globalAccess) {
        List<Facility> facilities = globalAccess
                ? facilityRepository.findAll(Sort.by("name"))
                : facilityRepository.findAssignedTo(email);
        return facilities.stream().map(facilityMapper::toResponse).toList();
    }

    @Transactional
    public FacilityResponseDTO create(CreateFacilityDTO dto) {
        if (facilityRepository.existsByCode(dto.code())) {
            throw new DuplicateResourceException("un establecimiento", "el código", dto.code());
        }
        checkRenipress(null, dto.renipressCode());
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
        checkRenipress(facility.getRenipressCode(), dto.renipressCode());
        facility.setRenipressCode(dto.renipressCode() == null || dto.renipressCode().isBlank()
                ? null : dto.renipressCode().trim());
        facility.setBeds(dto.beds());
        facility.setOccupancyRate(dto.occupancyRate());
        return facilityMapper.toResponse(facility);
    }

    @Transactional
    public void delete(Long id) {
        facilityRepository.delete(getOrThrow(id));
    }

    /** Un código RENIPRESS identifica a una sola IPRESS. current: el que ya tiene el establecimiento (o null). */
    private void checkRenipress(String current, String requested) {
        if (requested == null || requested.isBlank() || requested.trim().equals(current)) {
            return;
        }
        if (facilityRepository.existsByRenipressCode(requested.trim())) {
            throw new DuplicateResourceException("un establecimiento", "el código RENIPRESS", requested.trim());
        }
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


