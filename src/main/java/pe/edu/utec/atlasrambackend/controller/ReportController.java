package pe.edu.utec.atlasrambackend.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utec.atlasrambackend.report.ProaReportService;

import java.time.LocalDate;

@RestController
@RequestMapping("/reports")
public class ReportController {

    private final ProaReportService proaReportService;

    public ReportController(ProaReportService proaReportService) {
        this.proaReportService = proaReportService;
    }

    /** Reporte PROA del establecimiento en PDF (NTS 184): antibiograma acumulado y alertas activas. */
    @GetMapping(value = "/proa", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("@facilityAccess.canAccess(authentication, #facilityId)")
    public ResponseEntity<byte[]> proa(@RequestParam Long facilityId,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] pdf = proaReportService.generate(facilityId, from, to);
        String filename = "reporte-proa-" + facilityId + "-" + from + "-" + to + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(pdf);
    }
}
