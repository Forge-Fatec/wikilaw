package forge.wikilaw.backend.controller;

import forge.wikilaw.backend.dto.ImportJobResponse;
import forge.wikilaw.backend.service.ImportJobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/integrations/tarefas")
public class ImportJobController {
    private final ImportJobService service;
    public ImportJobController(ImportJobService service) { this.service = service; }

    @GetMapping("/{id}")
    public ImportJobResponse consultar(@PathVariable Long id) { return service.get(id); }

    @PostMapping("/{id}/retomar")
    public ResponseEntity<ImportJobResponse> retomar(@PathVariable Long id,
            @RequestParam(defaultValue = "50") int maximoPaginas) {
        return accepted(service.resume(id, maximoPaginas));
    }

    @PostMapping("/{id}/cancelar")
    public ImportJobResponse cancelar(@PathVariable Long id) { return service.cancel(id); }

    public static ResponseEntity<ImportJobResponse> accepted(ImportJobResponse job) {
        return ResponseEntity.accepted().location(URI.create("/api/integrations/tarefas/" + job.idTarefa())).body(job);
    }
}
