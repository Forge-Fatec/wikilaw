package forge.wikilaw.backend.controller;

import forge.wikilaw.backend.dto.AnaliseJuridicaRequest;
import forge.wikilaw.backend.dto.AnaliseJuridicaResponse;
import forge.wikilaw.backend.service.AnaliseJuridicaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pesquisas/analises")
public class AnaliseJuridicaController {
    private final AnaliseJuridicaService service;

    public AnaliseJuridicaController(AnaliseJuridicaService service) {
        this.service = service;
    }

    @PostMapping
    public AnaliseJuridicaResponse analisar(@Valid @RequestBody AnaliseJuridicaRequest request) {
        return service.analisar(request);
    }
}
