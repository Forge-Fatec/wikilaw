package forge.wikilaw.backend.controller;

import forge.wikilaw.backend.dto.SemanticSearchRequest;
import forge.wikilaw.backend.service.semantic.SemanticSearchService;
import forge.wikilaw.backend.service.semantic.SemanticSearchService.SemanticSearchResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** POST porque a descricao do caso pode ser longa demais para a URL. */
@RestController
@RequestMapping("/api/pesquisas/semantica")
public class SemanticSearchController {
    private final SemanticSearchService service;

    public SemanticSearchController(SemanticSearchService service) {
        this.service = service;
    }

    @PostMapping
    public SemanticSearchResult buscar(@Valid @RequestBody SemanticSearchRequest request) {
        return service.buscar(request.toQuery(), request.categoriasSelecionadas(), request.limiteOuPadrao());
    }
}
