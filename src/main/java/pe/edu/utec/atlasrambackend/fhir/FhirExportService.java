package pe.edu.utec.atlasrambackend.fhir;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.model.Interpretation;
import pe.edu.utec.atlasrambackend.model.Isolate;
import pe.edu.utec.atlasrambackend.model.MicQualifier;
import pe.edu.utec.atlasrambackend.model.SusceptibilityResult;
import pe.edu.utec.atlasrambackend.repository.IsolateRepository;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Exporta un aislamiento y su antibiograma como HL7 FHIR R4: un Bundle "collection" con la muestra
 * (Specimen), el microorganismo identificado (Observation, SNOMED CT), una Observation por antibiótico
 * (LOINC por CIM o por disco, valor con su comparador, interpretación S/I/R de HL7 v3) y el informe que
 * las agrupa (DiagnosticReport).
 *
 * <p>No incluye datos del paciente: Atlas RAM solo guarda un seudónimo y no lo exporta. Es el formato
 * con el que un sistema hospitalario (HIS) o de laboratorio (LIS) puede recibir el resultado.</p>
 */
@Service
public class FhirExportService {

    static final String INTERPRETATION_SYSTEM = "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation";
    static final String CATEGORY_SYSTEM = "http://terminology.hl7.org/CodeSystem/observation-category";
    static final String UCUM = "http://unitsofmeasure.org";

    private final IsolateRepository isolateRepository;

    public FhirExportService(IsolateRepository isolateRepository) {
        this.isolateRepository = isolateRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> isolateBundle(Long isolateId) {
        Isolate isolate = isolateRepository.findByIdWithRelations(isolateId);
        if (isolate == null) {
            throw new ResourceNotFoundException("Aislamiento", isolateId);
        }
        return toBundle(isolate);
    }

    static Map<String, Object> toBundle(Isolate isolate) {
        String specimenUrl = urn(isolate.getId(), "specimen");
        String organismUrl = urn(isolate.getId(), "organism");
        String date = isolate.getCollectionDate().toString();

        List<Map<String, Object>> entries = new ArrayList<>();
        entries.add(entry(specimenUrl, specimen(isolate)));

        List<Map<String, Object>> members = new ArrayList<>();
        List<SusceptibilityResult> results = new ArrayList<>(isolate.getResults());
        results.sort(Comparator.comparing(r -> r.getAntibiotic().getCode()));
        List<Map<String, Object>> susceptibilityEntries = new ArrayList<>();
        for (SusceptibilityResult result : results) {
            String url = urn(isolate.getId(), "ast-" + result.getAntibiotic().getCode());
            members.add(Map.of("reference", url));
            susceptibilityEntries.add(entry(url, susceptibility(result, specimenUrl, date)));
        }

        Map<String, Object> organism = observation(date, specimenUrl);
        organism.put("code", Map.of("text", "Microorganismo identificado"));
        Map<String, Object> organismConcept = new LinkedHashMap<>();
        Terminology.snomed(isolate.getMicroorganism().getCode()).ifPresent(code -> organismConcept.put("coding",
                List.of(coding(Terminology.SNOMED, code, organismName(isolate) + " (organism)"))));
        organismConcept.put("text", organismName(isolate));
        organism.put("valueCodeableConcept", organismConcept);
        if (!members.isEmpty()) {
            organism.put("hasMember", members);
        }
        entries.add(entry(organismUrl, organism));
        entries.addAll(susceptibilityEntries);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("resourceType", "DiagnosticReport");
        report.put("status", "final");
        report.put("category", List.of(Map.of("coding", List.of(coding(
                "http://terminology.hl7.org/CodeSystem/v2-0074", "MB", "Microbiology")))));
        report.put("code", Map.of("text", "Cultivo y antibiograma"));
        report.put("effectiveDateTime", date);
        report.put("specimen", List.of(Map.of("reference", specimenUrl)));
        List<Map<String, Object>> reportResults = new ArrayList<>();
        reportResults.add(Map.of("reference", organismUrl));
        reportResults.addAll(members);
        report.put("result", reportResults);
        report.put("performer", List.of(Map.of("display", isolate.getFacility().getName())));
        entries.add(0, entry(urn(isolate.getId(), "report"), report));

        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("resourceType", "Bundle");
        bundle.put("id", "atlas-ram-isolate-" + isolate.getId());
        bundle.put("type", "collection");
        bundle.put("entry", entries);
        return bundle;
    }

    private static Map<String, Object> specimen(Isolate isolate) {
        Map<String, Object> specimen = new LinkedHashMap<>();
        specimen.put("resourceType", "Specimen");
        if (isolate.getLabIsolateId() != null) {
            specimen.put("accessionIdentifier", Map.of("value", isolate.getLabIsolateId()));
        }
        specimen.put("type", Map.of("text", isolate.getSpecimenType().name()));
        specimen.put("collection", Map.of("collectedDateTime", isolate.getCollectionDate().toString()));
        return specimen;
    }

    static Map<String, Object> susceptibility(SusceptibilityResult r, String specimenUrl, String date) {
        Map<String, Object> obs = observation(date, specimenUrl);
        String code = r.getAntibiotic().getCode();
        boolean byMic = r.getMicValue() != null;
        boolean byDisk = !byMic && r.getDiskDiffusionMm() != null;

        Map<String, Object> concept = new LinkedHashMap<>();
        Terminology.loinc(code).ifPresent(pair -> {
            if (byMic) {
                concept.put("coding", List.of(coding(Terminology.LOINC, pair.mic(),
                        r.getAntibiotic().getName() + " [Susceptibility] by Minimum inhibitory concentration (MIC)")));
            } else if (byDisk) {
                concept.put("coding", List.of(coding(Terminology.LOINC, pair.disk(),
                        r.getAntibiotic().getName() + " [Susceptibility] by Disk diffusion (KB)")));
            }
        });
        concept.put("text", r.getAntibiotic().getName() + " (" + code + ")");
        obs.put("code", concept);

        if (byMic) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("value", r.getMicValue());
            String comparator = comparator(r.getMicQualifier());
            if (comparator != null) {
                value.put("comparator", comparator);
            }
            value.put("unit", "ug/mL");
            value.put("system", UCUM);
            value.put("code", "ug/mL");
            obs.put("valueQuantity", value);
        } else if (byDisk) {
            obs.put("valueQuantity", Map.of("value", r.getDiskDiffusionMm(), "unit", "mm", "system", UCUM, "code", "mm"));
        }
        obs.put("interpretation", List.of(Map.of("coding", List.of(interpretation(r.getInterpretation())))));
        if (r.getBreakpointStandard() != null) {
            obs.put("note", List.of(Map.of("text", "Interpretado por Atlas RAM con puntos de corte "
                    + r.getBreakpointStandard() + " " + r.getBreakpointVersion())));
        } else {
            obs.put("note", List.of(Map.of("text", "Interpretación informada por el laboratorio")));
        }
        return obs;
    }

    /** FHIR usa los mismos símbolos que los equipos: ≤ → "<=". Un valor exacto no lleva comparador. */
    static String comparator(MicQualifier qualifier) {
        return qualifier == null || qualifier == MicQualifier.EQ ? null : qualifier.symbol();
    }

    static Map<String, Object> interpretation(Interpretation interpretation) {
        return switch (interpretation) {
            case SUSCEPTIBLE -> coding(INTERPRETATION_SYSTEM, "S", "Susceptible");
            case INTERMEDIATE -> coding(INTERPRETATION_SYSTEM, "I", "Intermediate");
            case RESISTANT -> coding(INTERPRETATION_SYSTEM, "R", "Resistant");
        };
    }

    private static Map<String, Object> observation(String date, String specimenUrl) {
        Map<String, Object> obs = new LinkedHashMap<>();
        obs.put("resourceType", "Observation");
        obs.put("status", "final");
        obs.put("category", List.of(Map.of("coding", List.of(coding(CATEGORY_SYSTEM, "laboratory", "Laboratory")))));
        obs.put("effectiveDateTime", date);
        obs.put("specimen", Map.of("reference", specimenUrl));
        return obs;
    }

    private static Map<String, Object> coding(String system, String code, String display) {
        Map<String, Object> coding = new LinkedHashMap<>();
        coding.put("system", system);
        coding.put("code", code);
        coding.put("display", display);
        return coding;
    }

    private static Map<String, Object> entry(String fullUrl, Map<String, Object> resource) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("fullUrl", fullUrl);
        entry.put("resource", resource);
        return entry;
    }

    private static String organismName(Isolate isolate) {
        var m = isolate.getMicroorganism();
        return m.getSpecies() == null ? m.getGenus() : m.getGenus() + " " + m.getSpecies();
    }

    /** Identificador estable por recurso: el mismo aislamiento exporta siempre los mismos UUID. */
    static String urn(Long isolateId, String part) {
        return "urn:uuid:" + UUID.nameUUIDFromBytes(("atlas-ram/isolate/" + isolateId + "/" + part)
                .getBytes(StandardCharsets.UTF_8));
    }
}
