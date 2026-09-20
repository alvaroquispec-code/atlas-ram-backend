package pe.edu.utec.atlasrambackend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.atlasrambackend.dto.AntibiogramResponseDTO;
import pe.edu.utec.atlasrambackend.exception.BusinessRuleException;
import pe.edu.utec.atlasrambackend.repository.SusceptibilityResultRepository;

import java.time.LocalDate;
import java.util.List;

@Service
public class AntibiogramService {

    private final SusceptibilityResultRepository resultRepository;

    public AntibiogramService(SusceptibilityResultRepository resultRepository) {
        this.resultRepository = resultRepository;
    }

    /**
     * Antibiograma acumulado. Solo devuelve combinaciones con al menos 30 resultados,
     * como recomienda CLSI M39; por debajo de ese umbral el porcentaje no es interpretable.
     */
    @Transactional(readOnly = true)
    public List<AntibiogramResponseDTO> calculate(Long microorganismId,
                                                  Long districtId,
                                                  LocalDate from,
                                                  LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessRuleException("La fecha inicial no puede ser posterior a la final");
        }
        return resultRepository.calculateAntibiogram(microorganismId, districtId, from, to);
    }
}
