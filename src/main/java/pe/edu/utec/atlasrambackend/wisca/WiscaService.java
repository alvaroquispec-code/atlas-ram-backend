package pe.edu.utec.atlasrambackend.wisca;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.dto.WiscaResponseDTO;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.model.Antibiotic;
import pe.edu.utec.atlasrambackend.model.Interpretation;
import pe.edu.utec.atlasrambackend.repository.AntibiogramQueries;
import pe.edu.utec.atlasrambackend.repository.AntibioticRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class WiscaService {

    static final int DRAWS = 10_000;
    /** Semilla fija: la misma consulta da siempre el mismo resultado (reproducible y auditable). */
    static final long SEED = 2031L;
    static final int MIN_ISOLATES = 30;
    static final int MAX_REGIMENS = 10;
    static final int MAX_DRUGS_PER_REGIMEN = 3;

    static final String METHOD = "WISCA bayesiano (Hebert 2012; Bielicki 2016): frecuencias de gérmenes "
            + "Dirichlet(n+1) y sensibilidad Beta(k+1, m−k+1), 10 000 simulaciones de Monte Carlo. Primer "
            + "aislamiento por paciente y especie (CLSI M39). El síndrome se aproxima por el tipo de muestra. "
            + "Un germen sin pruebas para un régimen se cuenta como no cubierto. Regla de CLSI M100 aplicada: "
            + "oxacilina de S. aureus para los betalactámicos.";

    private final AntibiogramQueries queries;
    private final AntibioticRepository antibioticRepository;

    public WiscaService(AntibiogramQueries queries, AntibioticRepository antibioticRepository) {
        this.queries = queries;
        this.antibioticRepository = antibioticRepository;
    }

    /**
     * @param regimens  "MEM", "TZP+AMK"… (null o vacío = los de la guía del síndrome). En una URL el "+"
     *                  llega como espacio, así que también se acepta el espacio como separador.
     */
    @Transactional(readOnly = true)
    public WiscaResponseDTO calculate(Syndrome syndrome, List<String> regimens, Long districtId, Long facilityId,
                                      LocalDate from, LocalDate to, double target) {
        if (from.isAfter(to)) {
            throw new BusinessRuleException("La fecha inicial no puede ser posterior a la final");
        }
        if (target <= 0 || target > 100) {
            throw new BusinessRuleException("La cobertura objetivo debe estar entre 0 y 100");
        }
        List<List<String>> parsed = parseRegimens(regimens == null || regimens.isEmpty()
                ? syndrome.defaultRegimens() : regimens);
        Map<String, Antibiotic> antibiotics = resolveAntibiotics(parsed);

        List<WiscaCalculator.IsolateProfile> profiles = toProfiles(queries.profiles(
                syndrome.specimen().name(), districtId, facilityId == null ? null : List.of(facilityId), from, to));
        WiscaCalculator.Result result = WiscaCalculator.compute(profiles, parsed, target, DRAWS, SEED);

        List<WiscaResponseDTO.Regimen> regimenDtos = result.regimens().stream()
                .map(r -> new WiscaResponseDTO.Regimen(
                        String.join("+", r.antibiotics()),
                        r.antibiotics().stream().map(code -> antibiotics.get(code).getName()).toList(),
                        r.median(), r.mean(), r.low(), r.high(),
                        r.probabilityAboveTarget(), r.probabilityBest(),
                        r.untestedFrequency(), r.untestedPathogens(),
                        r.byPathogen().stream().map(p -> new WiscaResponseDTO.PathogenCoverage(
                                p.microorganismName(), p.evaluated(), p.covered(), p.percentage())).toList()))
                .toList();

        return new WiscaResponseDTO(syndrome.name(), syndrome.label(), syndrome.specimen().name(), from, to,
                result.isolates(), result.isolates() >= MIN_ISOLATES, target, DRAWS,
                result.pathogens().stream()
                        .map(p -> new WiscaResponseDTO.Pathogen(p.microorganismName(), p.isolates(), p.frequency()))
                        .toList(),
                regimenDtos, METHOD);
    }

    static List<List<String>> parseRegimens(List<String> raw) {
        List<List<String>> regimens = new ArrayList<>();
        for (String item : raw) {
            if (item == null || item.isBlank()) {
                continue;
            }
            List<String> drugs = Arrays.stream(item.trim().toUpperCase(Locale.ROOT).split("[+\\s]+"))
                    .filter(code -> !code.isBlank()).distinct().toList();
            if (drugs.size() > MAX_DRUGS_PER_REGIMEN) {
                throw new BusinessRuleException("Un régimen admite como máximo " + MAX_DRUGS_PER_REGIMEN
                        + " antibióticos: " + item);
            }
            if (!regimens.contains(drugs)) {
                regimens.add(drugs);
            }
        }
        if (regimens.isEmpty()) {
            throw new BusinessRuleException("Indique al menos un régimen, por ejemplo MEM o TZP+AMK");
        }
        if (regimens.size() > MAX_REGIMENS) {
            throw new BusinessRuleException("Se pueden comparar como máximo " + MAX_REGIMENS + " regímenes");
        }
        return regimens;
    }

    private Map<String, Antibiotic> resolveAntibiotics(List<List<String>> regimens) {
        Map<String, Antibiotic> found = new HashMap<>();
        for (List<String> regimen : regimens) {
            for (String code : regimen) {
                if (!found.containsKey(code)) {
                    found.put(code, antibioticRepository.findByCode(code).orElseThrow(
                            () -> new BusinessRuleException("Antibiótico desconocido en el régimen: " + code)));
                }
            }
        }
        return found;
    }

    /** Agrupa las filas (una por resultado) en perfiles (uno por aislamiento). */
    static List<WiscaCalculator.IsolateProfile> toProfiles(List<AntibiogramQueries.ProfileRow> rows) {
        Map<Long, WiscaCalculator.IsolateProfile> byIsolate = new LinkedHashMap<>();
        for (AntibiogramQueries.ProfileRow row : rows) {
            byIsolate.computeIfAbsent(row.isolateId(), id -> new WiscaCalculator.IsolateProfile(
                    row.microorganismId(), row.microorganismCode(), row.microorganismName(), new HashMap<>()))
                    .results().put(row.antibioticCode(), Interpretation.valueOf(row.interpretation()));
        }
        return new ArrayList<>(byIsolate.values());
    }
}
