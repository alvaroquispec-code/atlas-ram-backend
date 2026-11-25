package pe.edu.utec.atlasrambackend.fhir;

import org.junit.jupiter.api.Test;
import pe.edu.utec.atlasrambackend.model.Antibiotic;
import pe.edu.utec.atlasrambackend.model.Facility;
import pe.edu.utec.atlasrambackend.model.Interpretation;
import pe.edu.utec.atlasrambackend.model.Isolate;
import pe.edu.utec.atlasrambackend.model.MicQualifier;
import pe.edu.utec.atlasrambackend.model.Microorganism;
import pe.edu.utec.atlasrambackend.model.Specimen;
import pe.edu.utec.atlasrambackend.model.SusceptibilityResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class FhirExportServiceTest {

    private static Isolate isolate() {
        Facility facility = new Facility();
        facility.setName("Hospital Nacional Hipólito Unanue");
        Microorganism kpn = new Microorganism();
        kpn.setCode("kpn");
        kpn.setGenus("Klebsiella");
        kpn.setSpecies("pneumoniae");
        Isolate isolate = new Isolate();
        isolate.setId(77L);
        isolate.setFacility(facility);
        isolate.setMicroorganism(kpn);
        isolate.setSpecimenType(Specimen.BLOOD);
        isolate.setCollectionDate(LocalDate.of(2026, 4, 20));
        isolate.setLabIsolateId("M-80-2");
        isolate.setPatientKey("seudonimo-que-no-debe-salir");
        isolate.addResult(result("MEM", "Meropenem", new BigDecimal("16"), MicQualifier.GE, null, Interpretation.RESISTANT));
        isolate.addResult(result("AMK", "Amikacina", null, null, 19, Interpretation.SUSCEPTIBLE));
        return isolate;
    }

    private static SusceptibilityResult result(String code, String name, BigDecimal mic, MicQualifier q, Integer disk,
                                               Interpretation interpretation) {
        Antibiotic antibiotic = new Antibiotic();
        antibiotic.setCode(code);
        antibiotic.setName(name);
        SusceptibilityResult r = new SusceptibilityResult();
        r.setAntibiotic(antibiotic);
        r.setMicValue(mic);
        r.setMicQualifier(q);
        r.setDiskDiffusionMm(disk);
        r.setInterpretation(interpretation);
        r.setBreakpointStandard("CLSI");
        r.setBreakpointVersion("2024");
        return r;
    }

    private static List<Map<String, Object>> resources(Map<String, Object> bundle) {
        return ((List<Map<String, Object>>) bundle.get("entry")).stream()
                .map(e -> (Map<String, Object>) e.get("resource")).toList();
    }

    private static Map<String, Object> firstCoding(Object concept) {
        return ((List<Map<String, Object>>) ((Map<String, Object>) concept).get("coding")).get(0);
    }

    @Test
    void elBundleTraeInformeMuestraGermenYUnaObservacionPorAntibiotico() {
        Map<String, Object> bundle = FhirExportService.toBundle(isolate());

        assertThat(bundle.get("resourceType")).isEqualTo("Bundle");
        assertThat(bundle.get("type")).isEqualTo("collection");
        List<String> types = resources(bundle).stream().map(r -> (String) r.get("resourceType")).toList();
        assertThat(types).containsExactly("DiagnosticReport", "Specimen", "Observation", "Observation", "Observation");

        Map<String, Object> organism = resources(bundle).get(2);
        assertThat(firstCoding(organism.get("valueCodeableConcept")).get("code")).isEqualTo("56415008");
        assertThat((List<?>) organism.get("hasMember")).hasSize(2);
    }

    @Test
    void cimConComparadorYDiscoEnMmConSusCodigosLoinc() {
        List<Map<String, Object>> r = resources(FhirExportService.toBundle(isolate()));
        Map<String, Object> amk = r.get(3);   // ordenados por código: AMK antes que MEM
        Map<String, Object> mem = r.get(4);

        assertThat(firstCoding(mem.get("code")).get("code")).isEqualTo("6652-2");
        Map<String, Object> memValue = (Map<String, Object>) mem.get("valueQuantity");
        assertThat(memValue.get("comparator")).isEqualTo(">=");
        assertThat(memValue.get("unit")).isEqualTo("ug/mL");
        assertThat(firstCoding(((List<?>) mem.get("interpretation")).get(0)).get("code")).isEqualTo("R");

        assertThat(firstCoding(amk.get("code")).get("code")).isEqualTo("13-3");
        assertThat(((Map<String, Object>) amk.get("valueQuantity")).get("unit")).isEqualTo("mm");
        assertThat(firstCoding(((List<?>) amk.get("interpretation")).get(0)).get("code")).isEqualTo("S");
    }

    @Test
    void noSaleNingunDatoDelPaciente() {
        String json = FhirExportService.toBundle(isolate()).toString();
        assertThat(json).doesNotContain("seudonimo-que-no-debe-salir");
        assertThat(json).doesNotContain("Patient");
    }

    @Test
    void losIdentificadoresSonEstables() {
        assertThat(FhirExportService.urn(77L, "organism")).isEqualTo(FhirExportService.urn(77L, "organism"));
        assertThat(FhirExportService.urn(77L, "organism")).isNotEqualTo(FhirExportService.urn(78L, "organism"));
        assertThat(FhirExportService.urn(77L, "organism")).startsWith("urn:uuid:");
    }
}
