package forge.wikilaw.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import forge.wikilaw.backend.dto.MarcoAcompanhamento;
import forge.wikilaw.backend.dto.NovidadesAcompanhamentoRequest;
import forge.wikilaw.backend.entity.DocumentoSemantico.Categoria;
import forge.wikilaw.backend.repository.DecisaoJudicialRepository;
import forge.wikilaw.backend.repository.DocumentoDoutrinarioRepository;
import forge.wikilaw.backend.repository.PrecedenteRepository;
import forge.wikilaw.backend.service.DocumentQueryService.Summary;
import forge.wikilaw.backend.service.semantic.SemanticCaseQuery;
import forge.wikilaw.backend.service.semantic.SemanticSearchService;
import forge.wikilaw.backend.service.semantic.SemanticSearchService.RelevantChunk;
import forge.wikilaw.backend.service.semantic.SemanticSearchService.SemanticSearchItem;
import forge.wikilaw.backend.service.semantic.SemanticSearchService.SemanticSearchResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AcompanhamentoServiceTest {
    private DecisaoJudicialRepository decisoes;
    private PrecedenteRepository precedentes;
    private DocumentoDoutrinarioRepository doutrina;
    private SemanticSearchService busca;
    private AcompanhamentoService service;

    @BeforeEach
    void setUp() {
        decisoes = mock(DecisaoJudicialRepository.class);
        precedentes = mock(PrecedenteRepository.class);
        doutrina = mock(DocumentoDoutrinarioRepository.class);
        busca = mock(SemanticSearchService.class);
        service = new AcompanhamentoService(decisoes, precedentes, doutrina, busca);
    }

    @Test
    void marcoIsTheLargestIdOfEachDocumentType() {
        when(decisoes.findMaxId()).thenReturn(120L);
        when(precedentes.findMaxId()).thenReturn(45L);
        when(doutrina.findMaxId()).thenReturn(0L);

        assertThat(service.marcoAtual()).isEqualTo(new MarcoAcompanhamento(120, 45, 0));
    }

    @Test
    void keepsOnlyResultsAddedAfterTheMarcoOfTheirOwnType() {
        var query = new SemanticCaseQuery("Desconto de emprestimo nao contratado na aposentadoria.", null, null, null);
        when(busca.buscar(eq(query), eq(List.of(Categoria.JURISPRUDENCIA, Categoria.PRECEDENTE)),
                eq(AcompanhamentoService.LIMITE_RESULTADOS)))
                .thenReturn(new SemanticSearchResult("gemini-embedding-001", List.of(
                        item(130L, "decisoes", 90),
                        item(46L, "precedentes", 80),
                        item(100L, "decisoes", 70),
                        item(45L, "precedentes", 60))));
        var request = new NovidadesAcompanhamentoRequest(query.descricao(), null, List.of("decisoes", "precedentes"),
                new MarcoAcompanhamento(120, 45, 0));

        var resultado = service.novidades(request);

        assertThat(resultado.modelo()).isEqualTo("gemini-embedding-001");
        assertThat(resultado.resultados())
                .extracting(novo -> novo.documento().categoria() + ":" + novo.documento().id())
                .containsExactly("decisoes:130", "precedentes:46");
    }

    @Test
    void returnsNoNoveltiesWhenEverythingIsOlderThanTheMarco() {
        when(busca.buscar(eq(new SemanticCaseQuery("caso", null, null, null)), eq(List.of()),
                eq(AcompanhamentoService.LIMITE_RESULTADOS)))
                .thenReturn(new SemanticSearchResult("gemini-embedding-001", List.of(item(10L, "doutrina", 90))));

        var resultado = service.novidades(
                new NovidadesAcompanhamentoRequest("caso", null, null, new MarcoAcompanhamento(0, 0, 10)));

        assertThat(resultado.resultados()).isEmpty();
    }

    private SemanticSearchItem item(Long id, String categoria, int percentual) {
        var summary = new Summary(id, "STJ", categoria, "ext-" + id, "Titulo " + id, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        return new SemanticSearchItem(summary, percentual / 100.0, percentual, 1,
                new RelevantChunk("EMENTA", "trecho " + id), "A ementa deste documento se aproxima do caso descrito.");
    }
}
