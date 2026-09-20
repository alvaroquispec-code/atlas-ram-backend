package pe.edu.utec.atlasrambackend.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utec.atlasrambackend.dto.DataUploadResponseDTO;
import pe.edu.utec.atlasrambackend.model.UploadStatus;
import pe.edu.utec.atlasrambackend.service.DataUploadService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/uploads")
public class DataUploadController {

    private final DataUploadService dataUploadService;

    public DataUploadController(DataUploadService dataUploadService) {
        this.dataUploadService = dataUploadService;
    }

    @GetMapping
    public ResponseEntity<Page<DataUploadResponseDTO>> findAll(
            @PageableDefault(size = 20, sort = "startedAt") Pageable pageable) {
        return ResponseEntity.ok(dataUploadService.findAll(pageable));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<DataUploadResponseDTO>> findByStatus(
            @PathVariable UploadStatus status) {
        return ResponseEntity.ok(dataUploadService.findByStatus(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DataUploadResponseDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(dataUploadService.findById(id));
    }

    @GetMapping("/{id}/isolates/count")
    public ResponseEntity<Map<String, Long>> countIsolates(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("isolates", dataUploadService.countIsolates(id)));
    }
}
