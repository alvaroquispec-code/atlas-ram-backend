package pe.edu.utec.atlasrambackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pe.edu.utec.atlasrambackend.event.EventPublisher;
import pe.edu.utec.atlasrambackend.ingest.PatientKeyService;
import pe.edu.utec.atlasrambackend.model.Antibiotic;
import pe.edu.utec.atlasrambackend.model.DataUpload;
import pe.edu.utec.atlasrambackend.model.Facility;
import pe.edu.utec.atlasrambackend.model.Interpretation;
import pe.edu.utec.atlasrambackend.model.InterpretationSource;
import pe.edu.utec.atlasrambackend.model.Isolate;
import pe.edu.utec.atlasrambackend.model.Microorganism;
import pe.edu.utec.atlasrambackend.model.UploadStatus;
import pe.edu.utec.atlasrambackend.model.User;
import pe.edu.utec.atlasrambackend.repository.AntibioticRepository;
import pe.edu.utec.atlasrambackend.repository.DataUploadRepository;
import pe.edu.utec.atlasrambackend.repository.DistrictRepository;
import pe.edu.utec.atlasrambackend.repository.IsolateRepository;
import pe.edu.utec.atlasrambackend.repository.MicroorganismRepository;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Agrupación de filas en aislamientos y validaciones de cada fila durante la carga. */
class UploadServiceTest {

    private static final String HEADER =
            "fecha_toma,tipo_muestra,edad,sexo,codigo_establecimiento,codigo_microorganismo,"
                    + "codigo_antibiotico,interpretacion_laboratorio,id_paciente,id_aislamiento\n";

    private IsolateRepository isolateRepository;
    private UploadService service;
    private DataUpload upload;

    @BeforeEach
    void setUp() {
        DataUploadRepository uploadRepository = mock(DataUploadRepository.class);
        MicroorganismRepository microorganismRepository = mock(MicroorganismRepository.class);
        AntibioticRepository antibioticRepository = mock(AntibioticRepository.class);
        InterpretationService interpretationService = mock(InterpretationService.class);
        isolateRepository = mock(IsolateRepository.class);

        Facility hnch = new Facility();
        hnch.setId(1L);
        hnch.setCode("HNCH");
        User lab = new User();
        lab.setEmail("lab.hnch@atlasram.test");
        upload = new DataUpload();
        upload.setId(500L);
        upload.setFacility(hnch);
        upload.setUploadedBy(lab);
        when(uploadRepository.findById(500L)).thenReturn(Optional.of(upload));

        Microorganism eco = new Microorganism();
        eco.setId(1L);
        eco.setCode("eco");
        when(microorganismRepository.findByCode("eco")).thenReturn(Optional.of(eco));
        when(antibioticRepository.findByCode(anyString())).thenAnswer(inv -> {
            Antibiotic a = new Antibiotic();
            a.setCode(inv.getArgument(0));
            return Optional.of(a);
        });
        // La interpretación no es lo que se prueba aquí: se acepta la del laboratorio.
        when(interpretationService.resolve(any(), any(), any(), any())).thenAnswer(inv -> Optional.of(
                new InterpretationService.Resolution(inv.getArgument(1), InterpretationSource.LAB_REPORTED, null, null)));
        when(isolateRepository.findUploadIdsOfLabIsolate(any(), anyString(), any())).thenReturn(List.of());

        service = new UploadService(uploadRepository, mock(DistrictRepository.class), microorganismRepository,
                antibioticRepository, isolateRepository, interpretationService, mock(AlertService.class),
                mock(EventPublisher.class),
                new PatientKeyService(Base64.getEncoder().encodeToString(new byte[32])));
    }

    @SuppressWarnings("unchecked")
    private List<Isolate> run(String csv) {
        service.process(500L, new CsvParser().parse(csv.getBytes(StandardCharsets.UTF_8)));
        ArgumentCaptor<List<Isolate>> saved = ArgumentCaptor.forClass(List.class);
        verify(isolateRepository).saveAll(saved.capture());
        return new ArrayList<>(saved.getValue());
    }

    @Test
    void dosPacientesConLosMismosDatosYaNoSeFusionan() {
        // Misma fecha, muestra, edad y sexo. Antes eran un solo aislamiento y la segunda fila de MEM fallaba.
        List<Isolate> isolates = run(HEADER
                + "2026-08-20,URINE,47,F,HNCH,eco,MEM,S,HC-001,\n"
                + "2026-08-20,URINE,47,F,HNCH,eco,MEM,R,HC-002,\n");

        assertThat(isolates).hasSize(2);
        assertThat(isolates).extracting(Isolate::getPatientKey).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(upload.getProcessedRows()).isEqualTo(2);
    }

    @Test
    void lasFilasDelMismoAislamientoSeAgrupanPorSuCodigo() {
        List<Isolate> isolates = run(HEADER
                + "2026-08-20,ORINA,47,F,HNCH,eco,MEM,S,HC-001,M-77\n"
                + "2026-08-20,ORINA,47,F,,eco,CRO,R,HC-001,M-77\n");

        assertThat(isolates).hasSize(1);
        assertThat(isolates.get(0).getResults()).hasSize(2);
        assertThat(isolates.get(0).getLabIsolateId()).isEqualTo("M-77");
    }

    @Test
    void unaFilaNoPuedeCargarseAOtroEstablecimiento() {
        // El permiso se comprobó sobre HNCH; la fila no puede colar datos en otro hospital.
        List<Isolate> isolates = run(HEADER
                + "2026-08-20,URINE,47,F,HNHU,eco,MEM,S,HC-001,\n"
                + "2026-08-20,URINE,50,M,HNCH,eco,MEM,S,HC-002,\n");

        assertThat(isolates).hasSize(1);
        assertThat(upload.getFailedRows()).isEqualTo(1);
        assertThat(upload.getErrorLog()).contains("Línea 2").contains("no coincide");
    }

    @Test
    void unAislamientoYaRegistradoEnOtraCargaSeRechaza() {
        when(isolateRepository.findUploadIdsOfLabIsolate(eq(1L), eq("M-77"), eq(1L))).thenReturn(List.of(413L));

        run(HEADER + "2026-08-20,URINE,47,F,HNCH,eco,MEM,S,HC-001,M-77\n");

        assertThat(upload.getProcessedRows()).isZero();
        assertThat(upload.getErrorLog()).contains("ya se registró en la carga 413");
        assertThat(upload.getStatus()).isEqualTo(UploadStatus.COMPLETED);
    }

    @Test
    void elMismoCodigoConDatosDistintosEsUnError() {
        run(HEADER
                + "2026-08-20,URINE,47,F,HNCH,eco,MEM,S,HC-001,M-77\n"
                + "2026-08-20,URINE,48,F,HNCH,eco,CRO,S,HC-001,M-77\n");

        assertThat(upload.getErrorLog()).contains("Línea 3").contains("datos de paciente o muestra distintos");
    }

    @Test
    void elRegistroDeErroresSeRecortaPeroDiceCuantosFaltan() {
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < UploadService.MAX_ERROR_LINES + 5; i++) {
            errors.add("Línea " + i + ": error");
        }
        assertThat(UploadService.errorLog(errors)).endsWith("y 5 errores más");
    }

    @Test
    void interpretacionesDelLaboratorioEnFormatoWhonet() {
        List<Isolate> isolates = run(HEADER + "2026-08-20,URINE,47,F,HNCH,eco,MEM,R,HC-001,\n");

        assertThat(isolates.get(0).getResults().get(0).getInterpretation()).isEqualTo(Interpretation.RESISTANT);
    }
}
