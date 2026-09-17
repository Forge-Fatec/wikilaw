package forge.wikilaw.backend.controller;

import forge.wikilaw.backend.integration.datajud.dto.CargaResumoResponse;
import forge.wikilaw.backend.integration.datajud.dto.DataJudImportRequest;
import forge.wikilaw.backend.service.DataJudImportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations/datajud")
public class DataJudIntegrationController {

    private final DataJudImportService importService;

    public DataJudIntegrationController(DataJudImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/import")
    public ResponseEntity<CargaResumoResponse> importar(@Valid @RequestBody DataJudImportRequest request) {
        return ResponseEntity.ok(importService.importar(request));
    }
}
