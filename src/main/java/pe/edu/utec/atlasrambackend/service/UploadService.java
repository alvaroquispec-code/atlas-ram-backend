package pe.edu.utec.atlasrambackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.dto.CsvRowDTO;
import pe.edu.utec.atlasrambackend.event.EventPublisher;
import pe.edu.utec.atlasrambackend.event.UploadCompletedEvent;
import pe.edu.utec.atlasrambackend.event.UploadFailedEvent;
import pe.edu.utec.atlasrambackend.model.*;
import pe.edu.utec.atlasrambackend.repository.*;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);
    private static final int MAX_ERROR_LINES = 100;

    private final CsvParser csvParser;
    private final DataUploadRepository uploadRepository;
    private final FacilityRepository facilityRepository;
    private final DistrictRepository districtRepository;
    private final MicroorganismRepository microorganismRepository;
    private final AntibioticRepository antibioticRepository;
    private final IsolateRepository isolateRepository;
    private final InterpretationService interpretationService;
    private final EventPublisher eventPublisher;

    public UploadService(CsvParser csvParser,
                         DataUploadRepository uploadRepository,
                         FacilityRepository facilityRepository,
                         DistrictRepository districtRepository,
                         MicroorganismRepository microorganismRepository,
                         AntibioticRepository antibioticRepository,
                         IsolateRepository isolateRepository,
                         InterpretationService interpretationService,
                         EventPublisher eventPublisher) {
        this.csvParser = csvParser;
        this.uploadRepository = uploadRepository;
        this.facilityRepository = facilityRepository;
        this.districtRepository = districtRepository;
        this.microorganismRepository = microorganismRepository;
        this.antibioticRepository = antibioticRepository;
        this.isolateRepository = isolateRepository;
        this.interpretationService = interpretationService;
        this.eventPublisher = eventPublisher;
    }

    @Async("uploadExecutor")
    @Transactional
    public void process(Long uploadId, byte[] content) {
        DataUpload upload = uploadRepository.findById(uploadId).orElseThrow();
        upload.setStatus(UploadStatus.PROCESSING);
        uploadRepository.saveAndFlush(upload);

        List<String> errors = new ArrayList<>();
        int processed = 0;

        try {
            List<CsvRowDTO> rows = csvParser.parse(new ByteArrayInputStream(content));
            upload.setTotalRows(rows.size());

            Map<String, Isolate> isolates = new HashMap<>();

            for (CsvRowDTO row : rows) {
                try {
                    Isolate isolate = resolveIsolate(row, upload, isolates);
                    addResult(isolate, row);
                    processed++;
                } catch (Exception e) {
                    if (errors.size() < MAX_ERROR_LINES) {
                        errors.add("Línea " + row.lineNumber() + ": " + e.getMessage());
                    }
                }
            }

            isolateRepository.saveAll(isolates.values());

            upload.setProcessedRows(processed);
            upload.setFailedRows(rows.size() - processed);
            upload.setStatus(UploadStatus.COMPLETED);
            upload.setFinishedAt(Instant.now());
            upload.setErrorLog(errors.isEmpty() ? null : String.join("\n", errors));
            uploadRepository.save(upload);

            eventPublisher.publish(new UploadCompletedEvent(
                    upload.getId(), upload.getFileName(),
                    upload.getUploadedBy().getEmail(),
                    upload.getUploadedBy().getFullName(),
                    rows.size(), processed, rows.size() - processed));

        } catch (Exception e) {
            log.error("Carga {} falló: {}", uploadId, e.getMessage(), e);

            upload.setStatus(UploadStatus.FAILED);
            upload.setFinishedAt(Instant.now());
            upload.setErrorLog(e.getMessage());
            uploadRepository.save(upload);

            eventPublisher.publish(new UploadFailedEvent(
                    upload.getId(), upload.getFileName(),
                    upload.getUploadedBy().getEmail(),
                    upload.getUploadedBy().getFullName(),
                    e.getMessage()));
        }
    }

    private Isolate resolveIsolate(CsvRowDTO row, DataUpload upload,
                                   Map<String, Isolate> cache) {
        String key = String.join("|", row.facilityCode(), row.collectionDate(),
                row.specimenType(), row.microorganismCode(),
                row.patientAge(), row.patientSex());

        return cache.computeIfAbsent(key, k -> {
            Facility facility = facilityRepository.findByCode(row.facilityCode())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Establecimiento desconocido: " + row.facilityCode()));

            Microorganism microorganism = microorganismRepository
                    .findByCode(row.microorganismCode())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Microorganismo desconocido: " + row.microorganismCode()));

            Isolate isolate = new Isolate();
            isolate.setFacility(facility);
            isolate.setMicroorganism(microorganism);
            isolate.setCollectionDate(LocalDate.parse(row.collectionDate()));
            isolate.setSpecimenType(Specimen.valueOf(row.specimenType().toUpperCase()));
            isolate.setDataUpload(upload);

            if (!row.patientAge().isBlank()) {
                isolate.setPatientAge(Integer.parseInt(row.patientAge()));
            }
            if (!row.patientSex().isBlank()) {
                isolate.setPatientSex(row.patientSex().toUpperCase());
            }
            if (!row.residenceUbigeo().isBlank()) {
                districtRepository.findByUbigeo(row.residenceUbigeo())
                        .ifPresent(isolate::setDistrict);
            }

            return isolate;
        });
    }

    private void addResult(Isolate isolate, CsvRowDTO row) {
        Antibiotic antibiotic = antibioticRepository.findByCode(row.antibioticCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Antibiótico desconocido: " + row.antibioticCode()));

        boolean duplicated = isolate.getResults().stream()
                .anyMatch(r -> r.getAntibiotic().getCode().equals(row.antibioticCode()));
        if (duplicated) {
            throw new IllegalArgumentException(
                    "Antibiótico repetido para el mismo aislamiento: " + row.antibioticCode());
        }

        SusceptibilityResult result = new SusceptibilityResult();
        result.setAntibiotic(antibiotic);

        if (!row.micValue().isBlank()) {
            result.setMicValue(new BigDecimal(row.micValue()));
        }

        Interpretation reported = null;
        if (!row.reportedInterpretation().isBlank()) {
            reported = Interpretation.valueOf(row.reportedInterpretation().toUpperCase());
            result.setReportedInterpretation(reported);
        }

        Interpretation computed = interpretationService.interpret(
                        result.getMicValue(), isolate.getMicroorganism(),
                        antibiotic, "CLSI", "2024")
                .orElse(reported);

        if (computed == null) {
            throw new IllegalArgumentException(
                    "No hay CIM ni interpretación del laboratorio para " + row.antibioticCode());
        }

        result.setInterpretation(computed);
        result.setBreakpointStandard("CLSI");
        result.setBreakpointVersion("2024");

        isolate.addResult(result);
    }
}
