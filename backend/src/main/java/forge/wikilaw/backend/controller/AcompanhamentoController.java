package forge.wikilaw.backend.controller;

import forge.wikilaw.backend.dto.MarcoAcompanhamento;
import forge.wikilaw.backend.dto.NovidadesAcompanhamentoRequest;
import forge.wikilaw.backend.service.AcompanhamentoService;
import forge.wikilaw.backend.service.semantic.SemanticSearchService.SemanticSearchResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Pesquisas acompanhadas: o navegador guarda cada uma; o servidor nao salva nada. */
@RestController
@RequestMapping("/api/acompanhamentos")
public class AcompanhamentoController {
    private final AcompanhamentoService service;

    public AcompanhamentoController(AcompanhamentoService service) {
        this.service = service;
    }

    /** Marco gravado no navegador quando o usuario passa a acompanhar uma pesquisa. */
    @GetMapping("/marco")
    public MarcoAcompanhamento marco() {
        return service.marcoAtual();
    }

    /** POST porque a descricao do caso pode ser longa demais para a URL. */
    @PostMapping("/novidades")
    public SemanticSearchResult novidades(@Valid @RequestBody NovidadesAcompanhamentoRequest request) {
        return service.novidades(request);
    }
}
