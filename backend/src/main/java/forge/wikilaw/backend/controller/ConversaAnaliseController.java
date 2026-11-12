package forge.wikilaw.backend.controller;

import forge.wikilaw.backend.dto.PerguntaAnaliseRequest;
import forge.wikilaw.backend.dto.RespostaAnaliseResponse;
import forge.wikilaw.backend.service.ConversaAnaliseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Perguntas complementares sobre a analise juridica; a conversa fica no navegador. */
@RestController
@RequestMapping("/api/pesquisas/analises/perguntas")
public class ConversaAnaliseController {
    private final ConversaAnaliseService service;

    public ConversaAnaliseController(ConversaAnaliseService service) {
        this.service = service;
    }

    @PostMapping
    public RespostaAnaliseResponse perguntar(@Valid @RequestBody PerguntaAnaliseRequest request) {
        return service.responder(request);
    }
}
