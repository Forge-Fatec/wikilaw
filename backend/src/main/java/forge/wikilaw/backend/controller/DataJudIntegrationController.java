package forge.wikilaw.backend.controller;

import forge.wikilaw.backend.dto.ImportJobResponse;
import forge.wikilaw.backend.integration.datajud.dto.DataJudImportRequest;
import forge.wikilaw.backend.service.ImportJobService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/integrations/datajud")
public class DataJudIntegrationController {

    private final ImportJobService importService;

    public DataJudIntegrationController(ImportJobService importService) {
        this.importService = importService;
    }

    @PostMapping("/import")
    public ResponseEntity<ImportJobResponse> importar(@Valid @RequestBody DataJudImportRequest request,
            @RequestParam(defaultValue = "false") boolean incremental,
            @RequestParam(defaultValue = "false") boolean ateEsgotar) {
        return ImportJobController.accepted(importService.enqueueDataJud(request, incremental, ateEsgotar));
    }
}
