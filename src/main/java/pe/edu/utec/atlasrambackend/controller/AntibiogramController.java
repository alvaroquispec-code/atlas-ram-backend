package pe.edu.utec.atlasrambackend.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utec.atlasrambackend.dto.AntibiogramResponseDTO;
import pe.edu.utec.atlasrambackend.dto.DistrictResistanceResponseDTO;
import pe.edu.utec.atlasrambackend.dto.ReferralAntibiogramResponseDTO;
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
     * Consulta pública de resistencia acumulada (CLSI M39: primer aislamiento por paciente).
     * Ejemplo: /antibiogram?microorganismId=1&from=2025-01-01&to=2025-12-31
     * Con includeInsufficient=true también devuelve las combinaciones con menos de 30 aislamientos.
     */
    @GetMapping
    public ResponseEntity<List<AntibiogramResponseDTO>> calculate(
            @RequestParam(required = false) Long microorganismId,
            @RequestParam(required = false) Long districtId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean includeInsufficient) {
        return ResponseEntity.ok(
                antibiogramService.calculate(microorganismId, districtId, from, to, includeInsufficient));
    }

    /**
     * Antibiograma de los hospitales donde se atienden los pacientes de un distrito.
     * Ejemplo: /antibiogram/referral?districtId=4&from=2024-09-01&to=2026-08-31
     */
    @GetMapping("/referral")
    public ResponseEntity<ReferralAntibiogramResponseDTO> calculateReferral(
            @RequestParam Long districtId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(antibiogramService.calculateReferral(districtId, from, to));
    }

    /**
     * Resistencia por distrito para el mapa.
     * Ejemplo: /antibiogram/by-district?microorganismId=1&antibioticId=6&from=2025-01-01&to=2025-12-31
     */
    @GetMapping("/by-district")
    public ResponseEntity<List<DistrictResistanceResponseDTO>> calculateByDistrict(
            @RequestParam Long microorganismId,
            @RequestParam Long antibioticId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(
                antibiogramService.calculateByDistrict(microorganismId, antibioticId, from, to));
    }
}
