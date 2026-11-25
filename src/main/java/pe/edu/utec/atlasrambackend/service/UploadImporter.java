package pe.edu.utec.atlasrambackend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.dto.CsvRowDTO;
import pe.edu.utec.atlasrambackend.event.EventPublisher;
import pe.edu.utec.atlasrambackend.event.UploadCompletedEvent;
import pe.edu.utec.atlasrambackend.ingest.CsvValues;
import pe.edu.utec.atlasrambackend.ingest.PatientKeyService;
import pe.edu.utec.atlasrambackend.interpretation.MicParser;
import pe.edu.utec.atlasrambackend.interpretation.ParsedMic;
import pe.edu.utec.atlasrambackend.model.*;
import pe.edu.utec.atlasrambackend.repository.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UploadImporter {

    static final int MAX_ERROR_LINES = 100;
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final DataUploadRepository uploadRepository;
    private final DistrictRepository districtRepository;
    private final MicroorganismRepository microorganismRepository;
    private final AntibioticRepository antibioticRepository;
    private final IsolateRepository isolateRepository;
    private final InterpretationService interpretationService;
    private final AlertService alertService;
    private final EventPublisher eventPublisher;
    private final PatientKeyService patientKeyService;

    public UploadImporter(DataUploadRepository uploadRepository,
                         DistrictRepository districtRepository,
                         MicroorganismRepository microorganismRepository,
                         AntibioticRepository antibioticRepository,
                         IsolateRepository isolateRepository,
                         InterpretationService interpretationService,
                         AlertService alertService,
                         EventPublisher eventPublisher,
                         PatientKeyService patientKeyService) {
        this.uploadRepository = uploadRepository;
        this.districtRepository = districtRepository;
        this.microorganismRepository = microorganismRepository;
        this.antibioticRepository = antibioticRepository;
        this.isolateRepository = isolateRepository;
        this.interpretationService = interpretationService;
        this.alertService = alertService;
        this.eventPublisher = eventPublisher;
        this.patientKeyService = patientKeyService;
    }

    /**
     * Importa las filas y deja la carga COMPLETED, en una sola transacción: o entran todos los
     * aislamientos válidos y se evalúan las alertas, o no entra nada. Si algo falla aquí, la excepción
     * sube a UploadService, que marca la carga como FAILED en otra transacción.
     */
    @Transactional
    public void importRows(Long uploadId, CsvParser.Result parsed) {
        DataUpload upload = uploadRepository.findById(uploadId).orElseThrow();

        // Las filas mal formadas (número de columnas distinto) ya vienen descartadas por el lector.
        List<String> errors = new ArrayList<>(parsed.malformed());
        int processed = 0;
        int total = parsed.totalRows();
        upload.setTotalRows(total);

        Map<String, PendingIsolate> isolates = new HashMap<>();
        LocalDate today = LocalDate.now(LIMA);
        for (CsvRowDTO row : parsed.rows()) {
            try {
                Isolate isolate = resolveIsolate(row, upload, isolates, today);
                addResult(isolate, row);
                processed++;
            } catch (IllegalArgumentException e) {
                // Errores de validación de la fila. Un error de base de datos NO se atrapa aquí:
                // dejaría la transacción abortada y hay que abandonar la carga entera.
                errors.add("Línea " + row.lineNumber() + ": " + e.getMessage());
            }
        }

        isolateRepository.saveAll(isolates.values().stream()
                .map(PendingIsolate::isolate)
                .filter(i -> !i.getResults().isEmpty())
                .toList());

        upload.setProcessedRows(processed);
        upload.setFailedRows(total - processed);
        upload.setStatus(UploadStatus.COMPLETED);
        upload.setFinishedAt(Instant.now());
        upload.setErrorLog(errorLog(errors));
        uploadRepository.save(upload);

        eventPublisher.publish(new UploadCompletedEvent(
                upload.getId(), upload.getFileName(),
                upload.getUploadedBy().getEmail(),
                upload.getUploadedBy().getFullName(),
                total, processed, total - processed));

        alertService.checkAfterUpload(upload.getFacility());
    }

    /** Las primeras MAX_ERROR_LINES líneas y cuántas más hubo, para no guardar un registro gigante. */
    static String errorLog(List<String> errors) {
        if (errors.isEmpty()) {
            return null;
        }
        if (errors.size() <= MAX_ERROR_LINES) {
            return String.join("\n", errors);
        }
        return String.join("\n", errors.subList(0, MAX_ERROR_LINES))
                + "\n… y " + (errors.size() - MAX_ERROR_LINES) + " errores más";
    }

    /** Un aislamiento en construcción y los datos de la primera fila que lo creó, para detectar incoherencias. */
    private record PendingIsolate(Isolate isolate, String fingerprint) {
    }

    /**
     * Agrupa las filas del CSV en aislamientos (una fila = un antibiótico). La clave depende de lo que
     * envíe el laboratorio, de más a menos fiable:
     *   1. id_aislamiento: el código de la muestra en el laboratorio identifica el aislamiento.
     *   2. id_paciente: paciente + fecha + muestra + microorganismo.
     *   3. Ninguno: fecha + muestra + microorganismo + edad + sexo + distrito. Dos pacientes distintos con
     *      los mismos datos el mismo día se confundirían; por eso se recomiendan las columnas anteriores.
     */
    private Isolate resolveIsolate(CsvRowDTO row, DataUpload upload,
                                   Map<String, PendingIsolate> cache, LocalDate today) {
        Facility facility = upload.getFacility();
        // La fila no puede apuntar a otro establecimiento: el permiso se comprobó sobre el de la carga.
        if (!row.facilityCode().isBlank() && !row.facilityCode().equalsIgnoreCase(facility.getCode())) {
            throw new IllegalArgumentException("el establecimiento de la fila (" + row.facilityCode()
                    + ") no coincide con el de la carga (" + facility.getCode() + ")");
        }

        LocalDate collectionDate = CsvValues.parseDate(row.collectionDate(), today);
        Specimen specimen = CsvValues.parseSpecimen(row.specimenType());
        Integer age = CsvValues.parseAge(row.patientAge());
        String sex = CsvValues.parseSex(row.patientSex());
        String patientKey = patientKeyService.keyFor(facility.getCode(), row.patientId());
        String labIsolateId = row.labIsolateId().isBlank() ? null : row.labIsolateId();
        if (labIsolateId != null && labIsolateId.length() > 50) {
            throw new IllegalArgumentException("id_aislamiento supera los 50 caracteres");
        }

        Microorganism microorganism = microorganismRepository.findByCode(row.microorganismCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Microorganismo desconocido: " + row.microorganismCode()));

        String fingerprint = String.join("|", String.valueOf(collectionDate), specimen.name(),
                String.valueOf(age), String.valueOf(sex), String.valueOf(patientKey), row.residenceUbigeo());
        String key;
        if (labIsolateId != null) {
            key = "A|" + labIsolateId + "|" + microorganism.getId();
        } else if (patientKey != null) {
            key = "P|" + patientKey + "|" + collectionDate + "|" + specimen + "|" + microorganism.getId();
        } else {
            key = "L|" + fingerprint + "|" + microorganism.getId();
        }

        PendingIsolate existing = cache.get(key);
        if (existing != null) {
            if (!existing.fingerprint().equals(fingerprint)) {
                throw new IllegalArgumentException("el aislamiento " + labIsolateId
                        + " aparece con datos de paciente o muestra distintos a los de una fila anterior");
            }
            return existing.isolate();
        }

        if (labIsolateId != null) {
            List<Long> previous = isolateRepository.findUploadIdsOfLabIsolate(
                    facility.getId(), labIsolateId, microorganism.getId());
            if (!previous.isEmpty()) {
                throw new IllegalArgumentException("el aislamiento " + labIsolateId
                        + " ya se registró en la carga " + previous.get(0));
            }
        }

        Isolate isolate = new Isolate();
        isolate.setFacility(facility);
        isolate.setMicroorganism(microorganism);
        isolate.setCollectionDate(collectionDate);
        isolate.setSpecimenType(specimen);
        isolate.setPatientAge(age);
        isolate.setPatientSex(sex);
        isolate.setPatientKey(patientKey);
        isolate.setLabIsolateId(labIsolateId);
        isolate.setDataUpload(upload);
        isolate.setDataOrigin(upload.getDataOrigin());
        if (!row.residenceUbigeo().isBlank()) {
            districtRepository.findByUbigeo(row.residenceUbigeo()).ifPresent(isolate::setDistrict);
        }
        cache.put(key, new PendingIsolate(isolate, fingerprint));
        return isolate;
    }

    private void addResult(Isolate isolate, CsvRowDTO row) {
        Antibiotic antibiotic = antibioticRepository.findByCode(row.antibioticCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Antibiótico desconocido: " + row.antibioticCode()));

        boolean duplicated = isolate.getResults().stream()
                .anyMatch(r -> r.getAntibiotic().getCode().equals(row.antibioticCode()));
        if (duplicated) {
            throw new IllegalArgumentException("Antibiótico repetido para el mismo aislamiento: "
                    + row.antibioticCode() + (isolate.getLabIsolateId() == null && isolate.getPatientKey() == null
                    ? " (si son pacientes distintos, añada la columna id_paciente o id_aislamiento)" : ""));
        }

        SusceptibilityResult result = new SusceptibilityResult();
        result.setAntibiotic(antibiotic);

        // "<=0.25", ">=16", "0,5"... tal como los exportan los equipos y WHONET.
        ParsedMic mic = MicParser.parse(row.micValue()).orElse(null);
        if (mic != null) {
            result.setMicValue(mic.value());
            result.setMicQualifier(mic.qualifier());
        }

        Integer diskMm = parseDisk(row.diskMm(), row.antibioticCode());
        result.setDiskDiffusionMm(diskMm);

        Interpretation reported = CsvValues.parseInterpretation(row.reportedInterpretation());
        result.setReportedInterpretation(reported);

        InterpretationService.Resolution resolution = interpretationService
                .resolve(mic, diskMm, reported, isolate.getMicroorganism(), antibiotic, isolate.getSpecimenType())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se pudo interpretar " + row.antibioticCode()
                                + ": sin punto de corte (de CIM o de disco) o valor no concluyente,"
                                + " y sin interpretación del laboratorio"));

        result.setInterpretation(resolution.interpretation());
        result.setInterpretationSource(resolution.source());
        result.setBreakpointStandard(resolution.breakpointStandard());
        result.setBreakpointVersion(resolution.breakpointVersion());

        isolate.addResult(result);
    }

    /** Halo en mm: entero entre 1 y 99 ("18" o "18.0"). Vacío → sin disco. */
    static Integer parseDisk(String raw, String antibioticCode) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            double value = Double.parseDouble(raw.trim().replace(',', '.'));
            if (value != Math.rint(value) || value < 1 || value > 99) {
                throw new NumberFormatException();
            }
            return (int) value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Halo inválido para " + antibioticCode + ": '" + raw
                    + "' (se espera un entero en mm, de 1 a 99)");
        }
    }
}
