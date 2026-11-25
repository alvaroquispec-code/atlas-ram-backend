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
import pe.edu.utec.atlasrambackend.dto.TrendResponseDTO;
import pe.edu.utec.atlasrambackend.dto.WiscaResponseDTO;
import pe.edu.utec.atlasrambackend.service.AntibiogramService;
import pe.edu.utec.atlasrambackend.wisca.Syndrome;
import pe.edu.utec.atlasrambackend.wisca.WiscaService;
import pe.edu.utec.atlasrambackend.model.BreakpointSite;
import pe.edu.utec.atlasrambackend.model.DataOrigin;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/antibiogram")
public class AntibiogramController {

    private final AntibiogramService antibiogramService;
    private final WiscaService wiscaService;

    public AntibiogramController(AntibiogramService antibiogramService, WiscaService wiscaService) {
        this.antibiogramService = antibiogramService;
        this.wiscaService = wiscaService;
    }

    /**
     * Consulta pública de resistencia acumulada (CLSI M39: primer aislamiento por paciente).
     * Ejemplo: /antibiogram?microorganismId=1&from=2025-01-01&to=2025-12-31
     * Con includeInsufficient=true también devuelve las combinaciones con menos de 30 aislamientos.
     * origin (opcional, en todas las consultas): REAL o SYNTHETIC; sin él se usa toda procedencia.
     */
    @GetMapping
    public ResponseEntity<List<AntibiogramResponseDTO>> calculate(
            @RequestParam(required = false) Long microorganismId,
            @RequestParam(required = false) Long districtId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean includeInsufficient,
            @RequestParam(required = false) DataOrigin origin) {
        return ResponseEntity.ok(
                antibiogramService.calculate(microorganismId, districtId, from, to, includeInsufficient, origin));
    }

    /**
     * Antibiograma de los hospitales donde se atienden los pacientes de un distrito.
     * Ejemplo: /antibiogram/referral?districtId=4&from=2024-09-01&to=2026-08-31
     */
    @GetMapping("/referral")
    public ResponseEntity<ReferralAntibiogramResponseDTO> calculateReferral(
            @RequestParam Long districtId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) DataOrigin origin) {
        return ResponseEntity.ok(antibiogramService.calculateReferral(districtId, from, to, origin));
    }

    /**
     * Evolución de la resistencia de un par microorganismo-antibiótico, con prueba de tendencia.
     * Ejemplo: /antibiogram/trend?microorganismId=2&antibioticId=11&facilityId=6&from=2024-09-01&to=2026-08-31
     * interval: MONTH (por defecto) o QUARTER.
     */
    @GetMapping("/trend")
    public ResponseEntity<TrendResponseDTO> trend(
            @RequestParam Long microorganismId,
            @RequestParam Long antibioticId,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Long facilityId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "MONTH") AntibiogramService.TrendInterval interval,
            @RequestParam(required = false) DataOrigin origin,
            @RequestParam(required = false) BreakpointSite site) {
        return ResponseEntity.ok(antibiogramService.trend(
                microorganismId, antibioticId, districtId, facilityId, from, to, interval, origin, site));
    }

    /**
     * WISCA bayesiano: probabilidad de que cada régimen empírico cubra la infección de un síndrome.
     * Ejemplo: /antibiogram/wisca?syndrome=BACTEREMIA&from=2025-09-01&to=2026-08-31&regimens=MEM,TZP%2BAMK
     * Sin regimens se comparan los candidatos habituales del síndrome. target: cobertura objetivo (90 %).
     */
    @GetMapping("/wisca")
    public ResponseEntity<WiscaResponseDTO> wisca(
            @RequestParam Syndrome syndrome,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<String> regimens,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Long facilityId,
            @RequestParam(defaultValue = "90") double target,
            @RequestParam(required = false) DataOrigin origin) {
        return ResponseEntity.ok(wiscaService.calculate(syndrome, regimens, districtId, facilityId, from, to, target,
                origin));
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
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) DataOrigin origin,
            @RequestParam(required = false) BreakpointSite site) {
        return ResponseEntity.ok(
                antibiogramService.calculateByDistrict(microorganismId, antibioticId, from, to, origin, site));
    }
}
