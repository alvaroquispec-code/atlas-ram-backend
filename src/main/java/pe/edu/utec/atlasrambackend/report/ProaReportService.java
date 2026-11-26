package pe.edu.utec.atlasrambackend.report;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.dto.AntibiogramResponseDTO;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.exception.ResourceNotFoundException;
import pe.edu.utec.atlasrambackend.model.Facility;
import pe.edu.utec.atlasrambackend.model.ResistanceAlert;
import pe.edu.utec.atlasrambackend.repository.FacilityRepository;
import pe.edu.utec.atlasrambackend.repository.ResistanceAlertRepository;
import pe.edu.utec.atlasrambackend.service.AntibiogramService;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Reporte del establecimiento para el Comité de Optimización de Antimicrobianos (COAM) que exige la
 * NTS 184-MINSA/DIGEMID-2022 (PROA): antibiograma acumulado CLSI M39 del periodo y alertas de brote
 * activas. Se genera en el servidor como PDF para imprimir o adjuntar al acta del comité.
 *
 * Solo contiene agregados (porcentajes por microorganismo y antibiótico): ningún dato de pacientes.
 */
@Service
public class ProaReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Font TITLE = new Font(Font.HELVETICA, 15, Font.BOLD);
    private static final Font HEADING = new Font(Font.HELVETICA, 11, Font.BOLD);
    private static final Font BODY = new Font(Font.HELVETICA, 9, Font.NORMAL);
    private static final Font SMALL = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(90, 90, 90));
    private static final Font CELL_HEADER = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
    private static final Font CELL = new Font(Font.HELVETICA, 8, Font.NORMAL);
    private static final Font CELL_MUTED = new Font(Font.HELVETICA, 8, Font.ITALIC, new Color(120, 120, 120));
    private static final Color BRAND = new Color(30, 64, 120);
    /** Umbral orientativo de cobertura empírica: por debajo de 80 % de sensibilidad se resalta. */
    static final double LOW_SUSCEPTIBILITY = 80;

    private final FacilityRepository facilityRepository;
    private final ResistanceAlertRepository alertRepository;
    private final AntibiogramService antibiogramService;

    public ProaReportService(FacilityRepository facilityRepository,
                             ResistanceAlertRepository alertRepository,
                             AntibiogramService antibiogramService) {
        this.facilityRepository = facilityRepository;
        this.alertRepository = alertRepository;
        this.antibiogramService = antibiogramService;
    }

    @Transactional(readOnly = true)
    public byte[] generate(Long facilityId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessRuleException("La fecha inicial no puede ser posterior a la final");
        }
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Establecimiento", facilityId));
        List<AntibiogramResponseDTO> entries = antibiogramService.calculateForFacility(facilityId, from, to).stream()
                .sorted(Comparator.comparing(AntibiogramResponseDTO::microorganismName)
                        .thenComparing(AntibiogramResponseDTO::antibioticName))
                .toList();
        List<ResistanceAlert> alerts = alertRepository.findActiveByFacility(facilityId);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(document, out);
            document.open();
            header(document, facility, from, to);
            antibiogram(document, entries);
            alerts(document, alerts);
            methodology(document);
            document.close();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el reporte PROA", e);
        }
        return out.toByteArray();
    }

    private void header(Document document, Facility facility, LocalDate from, LocalDate to) throws Exception {
        Paragraph title = new Paragraph("Reporte PROA para el Comité de Optimización de Antimicrobianos", TITLE);
        document.add(title);
        document.add(new Paragraph("NTS 184-MINSA/DIGEMID-2022 · Atlas RAM", SMALL));
        document.add(Chunk.NEWLINE);
        String renipress = facility.getRenipressCode() == null ? "" : "  (RENIPRESS " + facility.getRenipressCode() + ")";
        document.add(new Paragraph("Establecimiento: " + facility.getName() + renipress, BODY));
        document.add(new Paragraph("Periodo: " + from.format(DATE) + " al " + to.format(DATE), BODY));
        document.add(new Paragraph("Generado el " + LocalDate.now().format(DATE), BODY));
        document.add(Chunk.NEWLINE);
    }

    private void antibiogram(Document document, List<AntibiogramResponseDTO> entries) throws Exception {
        document.add(new Paragraph("1. Antibiograma acumulado (CLSI M39, primer aislamiento por paciente)", HEADING));
        document.add(new Paragraph("Porcentaje de sensibilidad con intervalo de confianza de Wilson al 95 %. "
                + "Las filas con menos de 30 aislamientos se muestran en gris y no deben usarse para guías empíricas.",
                SMALL));
        document.add(Chunk.NEWLINE);
        if (entries.isEmpty()) {
            document.add(new Paragraph("Sin resultados en el periodo.", BODY));
            document.add(Chunk.NEWLINE);
            return;
        }
        PdfPTable table = new PdfPTable(new float[]{3.2f, 3.2f, 1f, 1.2f, 2f, 1.3f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String h : List.of("Microorganismo", "Antibiótico", "n", "% S", "IC 95 %", "% R")) {
            PdfPCell cell = new PdfPCell(new Phrase(h, CELL_HEADER));
            cell.setBackgroundColor(BRAND);
            cell.setPadding(4);
            table.addCell(cell);
        }
        for (AntibiogramResponseDTO e : entries) {
            boolean sufficient = e.sufficientData();
            Font font = sufficient ? CELL : CELL_MUTED;
            String antibiotic = e.antibioticName() + (e.breakpointSite() == null ? ""
                    : e.breakpointSite().name().equals("MENINGITIS") ? " (meningitis)" : " (no meningitis)");
            Color fill = sufficient && e.susceptiblePercentage() < LOW_SUSCEPTIBILITY ? new Color(253, 236, 234) : null;
            addCell(table, e.microorganismName(), font, fill, Element.ALIGN_LEFT);
            addCell(table, antibiotic, font, fill, Element.ALIGN_LEFT);
            addCell(table, String.valueOf(e.totalTested()), font, fill, Element.ALIGN_RIGHT);
            addCell(table, percent(e.susceptiblePercentage()), font, fill, Element.ALIGN_RIGHT);
            addCell(table, percent(e.susceptibleCiLow()) + " - " + percent(e.susceptibleCiHigh()), font, fill,
                    Element.ALIGN_RIGHT);
            addCell(table, percent(e.resistancePercentage()), font, fill, Element.ALIGN_RIGHT);
        }
        document.add(table);
        document.add(new Paragraph("Resaltado: sensibilidad menor de " + (int) LOW_SUSCEPTIBILITY
                + " % con al menos 30 aislamientos (umbral orientativo para revisar la terapia empírica).", SMALL));
        document.add(Chunk.NEWLINE);
    }

    private void alerts(Document document, List<ResistanceAlert> alerts) throws Exception {
        document.add(new Paragraph("2. Alertas de brote activas", HEADING));
        if (alerts.isEmpty()) {
            document.add(new Paragraph("No hay alertas abiertas ni reconocidas.", BODY));
            document.add(Chunk.NEWLINE);
            return;
        }
        for (ResistanceAlert a : alerts) {
            String organism = a.getMicroorganism().getGenus() + " " + a.getMicroorganism().getSpecies();
            String what = a.getAntibiotic() != null ? a.getAntibiotic().getName() : a.getAntibioticClass();
            document.add(new Paragraph(String.format(Locale.ROOT,
                    "- %s resistente a %s: %d de %d resistentes entre %s y %s (línea base %d de %d; p = %.1e). "
                            + "Detectada el %s. Estado: %s.",
                    organism, what, a.getResistantCount(), a.getTestedCount(), a.getWindowStart().format(DATE),
                    a.getWindowEnd().format(DATE), a.getBaselineResistant(), a.getBaselineTested(), a.getPValue(),
                    a.getDetectedOn().format(DATE), a.getStatus() == null ? "-" : statusLabel(a.getStatus().name())),
                    BODY));
        }
        document.add(Chunk.NEWLINE);
    }

    private void methodology(Document document) throws Exception {
        document.add(new Paragraph("3. Notas metodológicas", HEADING));
        document.add(new Paragraph("Interpretación S/I/R con los puntos de corte CLSI vigentes en el sistema; "
                + "cuando no hay punto de corte se usa la lectura informada por el laboratorio y queda marcada como tal. "
                + "Alertas: prueba binomial exacta de una cola que compara la ventana reciente con la línea base "
                + "del mismo establecimiento. Este reporte no reemplaza el criterio clínico del COAM.", SMALL));
    }

    private static void addCell(PdfPTable table, String text, Font font, Color fill, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(align);
        cell.setPadding(3);
        if (fill != null) {
            cell.setBackgroundColor(fill);
        }
        table.addCell(cell);
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "OPEN" -> "abierta";
            case "ACKNOWLEDGED" -> "reconocida";
            default -> status.toLowerCase(Locale.ROOT);
        };
    }

    static String percent(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }
}
