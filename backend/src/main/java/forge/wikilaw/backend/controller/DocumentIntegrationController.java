package forge.wikilaw.backend.controller;

import forge.wikilaw.backend.integration.documents.*;
import forge.wikilaw.backend.service.DocumentImportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integrations/documentos")
public class DocumentIntegrationController {
    private final DocumentImportService service;
    public DocumentIntegrationController(DocumentImportService service) { this.service=service; }
    @PostMapping("/{fonte}/import")
    public DocumentImportService.ImportResult importar(@PathVariable DocumentSource fonte,
            @Valid @RequestBody DocumentImportRequest request) {
        return service.importar(fonte,request);
    }
}
