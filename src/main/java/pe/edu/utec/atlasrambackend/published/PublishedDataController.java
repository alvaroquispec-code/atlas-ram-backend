package pe.edu.utec.atlasrambackend.published;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utec.atlasrambackend.model.DataOrigin;

import java.util.List;

/** Datos reales publicados (agregados de acceso abierto). Público, como el resto de /antibiogram. */
@RestController
public class PublishedDataController {

    private final PublishedDataService publishedDataService;

    public PublishedDataController(PublishedDataService publishedDataService) {
        this.publishedDataService = publishedDataService;
    }

    @Cacheable("published")
    @GetMapping("/antibiogram/published")
    public ResponseEntity<List<PublishedAntibiogramDTO>> findAll(@RequestParam(required = false) DataOrigin origin) {
        return ResponseEntity.ok(publishedDataService.findAll(origin));
    }
}
