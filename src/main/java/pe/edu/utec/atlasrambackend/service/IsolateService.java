package pe.edu.utec.atlasrambackend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.dto.CreateIsolateDTO;
import pe.edu.utec.atlasrambackend.dto.IsolateResponseDTO;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.mapper.IsolateMapper;
import pe.edu.utec.atlasrambackend.model.Isolate;
import pe.edu.utec.atlasrambackend.repository.DistrictRepository;
import pe.edu.utec.atlasrambackend.repository.FacilityRepository;
import pe.edu.utec.atlasrambackend.repository.IsolateRepository;
import pe.edu.utec.atlasrambackend.repository.MicroorganismRepository;

@Service
public class IsolateService {

    private final IsolateRepository isolateRepository;
    private final FacilityRepository facilityRepository;
    private final DistrictRepository districtRepository;
    private final MicroorganismRepository microorganismRepository;
    private final IsolateMapper isolateMapper;

    public IsolateService(IsolateRepository isolateRepository,
                          FacilityRepository facilityRepository,
                          DistrictRepository districtRepository,
                          MicroorganismRepository microorganismRepository,
                          IsolateMapper isolateMapper) {
        this.isolateRepository = isolateRepository;
        this.facilityRepository = facilityRepository;
        this.districtRepository = districtRepository;
        this.microorganismRepository = microorganismRepository;
        this.isolateMapper = isolateMapper;
    }

    @Transactional(readOnly = true)
    public Page<IsolateResponseDTO> findAll(Pageable pageable) {
        return isolateRepository.findAll(pageable).map(isolateMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<IsolateResponseDTO> findByFacility(Long facilityId, Pageable pageable) {
        return isolateRepository.findByFacilityId(facilityId, pageable).map(isolateMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public IsolateResponseDTO findById(Long id) {
        Isolate isolate = isolateRepository.findByIdWithRelations(id);
        if (isolate == null) {
            throw new ResourceNotFoundException("Aislamiento", id);
        }
        return isolateMapper.toResponse(isolate);
    }

    @Transactional
    public IsolateResponseDTO create(CreateIsolateDTO dto) {
        Isolate isolate = new Isolate();
        isolate.setCollectionDate(dto.collectionDate());
        isolate.setSpecimenType(dto.specimenType());
        isolate.setPatientAge(dto.patientAge());
        isolate.setPatientSex(dto.patientSex());
        isolate.setFacility(facilityRepository.findById(dto.facilityId())
                .orElseThrow(() -> new ResourceNotFoundException("Establecimiento", dto.facilityId())));
        isolate.setMicroorganism(microorganismRepository.findById(dto.microorganismId())
                .orElseThrow(() -> new ResourceNotFoundException("Microorganismo", dto.microorganismId())));
        if (dto.districtId() != null) {
            isolate.setDistrict(districtRepository.findById(dto.districtId())
                    .orElseThrow(() -> new ResourceNotFoundException("Distrito", dto.districtId())));
        }
        return isolateMapper.toResponse(isolateRepository.save(isolate));
    }

    @Transactional
    public void delete(Long id) {
        Isolate isolate = isolateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aislamiento", id));
        isolateRepository.delete(isolate);
    }
}
