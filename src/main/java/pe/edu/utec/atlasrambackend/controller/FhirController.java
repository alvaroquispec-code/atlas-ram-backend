package pe.edu.utec.atlasrambackend.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utec.atlasrambackend.fhir.FhirExportService;

import java.util.Map;

/** Interoperabilidad: resultados en HL7 FHIR R4 con códigos LOINC y SNOMED CT. */
@RestController
@RequestMapping("/fhir")
public class FhirController {

    static final MediaType FHIR_JSON = MediaType.parseMediaType("application/fhir+json");

    private final FhirExportService exportService;

    public FhirController(FhirExportService exportService) {
        this.exportService = exportService;
    }

    /** Aislamiento con su antibiograma como Bundle FHIR. Mismo control de acceso que GET /isolates/{id}. */
    @GetMapping("/isolates/{id}")
    @PreAuthorize("@facilityAccess.canAccessIsolate(authentication, #id)")
    public ResponseEntity<Map<String, Object>> isolate(@PathVariable Long id) {
        return ResponseEntity.ok().contentType(FHIR_JSON).body(exportService.isolateBundle(id));
    }
}
