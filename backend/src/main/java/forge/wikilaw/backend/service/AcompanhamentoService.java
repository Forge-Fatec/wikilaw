package forge.wikilaw.backend.service;

import forge.wikilaw.backend.dto.MarcoAcompanhamento;
import forge.wikilaw.backend.dto.NovidadesAcompanhamentoRequest;
import forge.wikilaw.backend.repository.DecisaoJudicialRepository;
import forge.wikilaw.backend.repository.DocumentoDoutrinarioRepository;
import forge.wikilaw.backend.repository.PrecedenteRepository;
import forge.wikilaw.backend.service.semantic.SemanticSearchService;
import forge.wikilaw.backend.service.semantic.SemanticSearchService.SemanticSearchResult;
import org.springframework.stereotype.Service;

/**
 * Acompanhamento de pesquisas sem estado no servidor: o navegador guarda a pesquisa e o marco, e aqui so se
 * calcula o marco atual e quais resultados da busca semantica entraram no acervo depois dele.
 */
@Service
public class AcompanhamentoService {
    /** Maximo da busca semantica: uma novidade so aparece se estiver entre os mais parecidos com o caso. */
    static final int LIMITE_RESULTADOS = 50;

    private final DecisaoJudicialRepository decisoes;
    private final PrecedenteRepository precedentes;
    private final DocumentoDoutrinarioRepository doutrina;
    private final SemanticSearchService busca;

    public AcompanhamentoService(DecisaoJudicialRepository decisoes, PrecedenteRepository precedentes,
            DocumentoDoutrinarioRepository doutrina, SemanticSearchService busca) {
        this.decisoes = decisoes;
        this.precedentes = precedentes;
        this.doutrina = doutrina;
        this.busca = busca;
    }

    public MarcoAcompanhamento marcoAtual() {
        return new MarcoAcompanhamento(decisoes.findMaxId(), precedentes.findMaxId(), doutrina.findMaxId());
    }

    public SemanticSearchResult novidades(NovidadesAcompanhamentoRequest request) {
        var resultado = busca.buscar(request.toQuery(), request.categoriasSelecionadas(), LIMITE_RESULTADOS);
        var novos = resultado.resultados().stream()
                .filter(item -> item.documento().id() > request.marco().idDe(item.documento().categoria()))
                .toList();
        return new SemanticSearchResult(resultado.modelo(), novos);
    }
}
