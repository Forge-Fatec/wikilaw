package forge.wikilaw.backend.controller;

import forge.wikilaw.backend.integration.documents.*;
import forge.wikilaw.backend.service.ImportJobService;
import forge.wikilaw.backend.dto.ImportJobResponse;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integrations/documentos")
public class DocumentIntegrationController {
    private final ImportJobService service;
    public DocumentIntegrationController(ImportJobService service) { this.service=service; }
    @PostMapping("/{fonte}/import")
    public ResponseEntity<ImportJobResponse> importar(@PathVariable DocumentSource fonte,
            @Valid @RequestBody DocumentImportRequest request,
            @RequestParam(defaultValue = "1") int maximoPaginas,
            @RequestParam(defaultValue = "false") boolean ateEsgotar) {
        return ImportJobController.accepted(service.enqueueDocuments(fonte,request,maximoPaginas,ateEsgotar || request.fullCollection()));
    }
}
