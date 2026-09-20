package pe.edu.utec.atlasrambackend.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utec.atlasrambackend.dto.AntibiogramResponseDTO;
import pe.edu.utec.atlasrambackend.service.AntibiogramService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/antibiogram")
public class AntibiogramController {

    private final AntibiogramService antibiogramService;

    public AntibiogramController(AntibiogramService antibiogramService) {
        this.antibiogramService = antibiogramService;
    }

    /**
     * Consulta pública de resistencia acumulada.
     * Ejemplo: /antibiogram?microorganismId=1&from=2025-01-01&to=2025-12-31
     */
    @GetMapping
    public ResponseEntity<List<AntibiogramResponseDTO>> calculate(
            @RequestParam(required = false) Long microorganismId,
            @RequestParam(required = false) Long districtId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(
                antibiogramService.calculate(microorganismId, districtId, from, to));
    }
}

