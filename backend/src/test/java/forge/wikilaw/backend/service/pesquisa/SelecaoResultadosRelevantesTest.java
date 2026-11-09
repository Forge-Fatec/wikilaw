package forge.wikilaw.backend.service.pesquisa;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.dto.PesquisaAtivaRequest;
import forge.wikilaw.backend.dto.PesquisaPdfRequest.DocumentoReferencia;
import forge.wikilaw.backend.integration.ai.ContextoJuridicoProperties;
import forge.wikilaw.backend.service.DocumentQueryService.Summary;
import forge.wikilaw.backend.service.pesquisa.PesquisaAtiva.ResultadoPesquisa;
import forge.wikilaw.backend.service.search.SearchTermProcessor;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelecaoResultadosRelevantesTest {

    private final ContextoJuridicoProperties limites = new ContextoJuridicoProperties();
    private final SelecaoResultadosRelevantes selecao = new SelecaoResultadosRelevantes(limites, new SearchTermProcessor());

    @BeforeEach
    void limitarATresDocumentos() {
        limites.setMaxDocumentos(3);
    }

    @Test
    void keepsEveryResultWhenTheyFitTheLimit() {
        var pesquisa = pesquisa("dano moral", "mais-recentes", "textual", "A", "B", "C");

        SelecaoResultados resultado = selecao.selecionar(pesquisa);

        assertThat(posicoes(resultado.selecionados())).containsExactly(1, 2, 3);
        assertThat(resultado.omitidos()).isEmpty();
    }

    @Test
    void prefersResultsThatMatchMoreSearchWordsWhenSortedByDate() {
        var pesquisa = pesquisa("dano moral negativação", "mais-recentes", "textual",
                "Contrato de locação",
                "Negativacao indevida gera DANO moral",
                "Dano material",
                "Execução fiscal",
                "Negativação e dano moral presumido",
                "Dano moral no transporte");

        SelecaoResultados resultado = selecao.selecionar(pesquisa);

        assertThat(posicoes(resultado.selecionados())).containsExactly(2, 5, 6);
        assertThat(posicoes(resultado.omitidos())).containsExactly(1, 3, 4);
    }

    @Test
    void breaksTiesByTheSearchPosition() {
        var pesquisa = pesquisa("usucapião", "mais-antigos", "textual", "A", "B", "C", "D", "E");

        assertThat(posicoes(selecao.selecionar(pesquisa).selecionados())).containsExactly(1, 2, 3);
    }

    @Test
    void keepsTheOrderOfResultsAlreadyRankedByRelevance() {
        var similaridade = pesquisa("dano moral", "relevancia", "similaridade", "Outro", "Outro", "Outro", "Dano moral");
        var relevancia = pesquisa("dano moral", "relevancia", "textual", "Outro", "Outro", "Outro", "Dano moral");

        assertThat(posicoes(selecao.selecionar(similaridade).selecionados())).containsExactly(1, 2, 3);
        assertThat(posicoes(selecao.selecionar(relevancia).omitidos())).containsExactly(4);
    }

    @Test
    void usesTheQueryBuiltFromTheAttachedDocument() {
        var filtros = new PesquisaAtivaRequest("textual", "rescisão contrato trabalho", "peticao.pdf", null, null,
                null, null, null, "mais-recentes", null, null, null);
        var pesquisa = new PesquisaAtiva(null, filtros,
                resultados("Dano moral", "Rescisão indireta do contrato de trabalho", "Furto", "Pensão", "Contrato"), 5);

        assertThat(posicoes(selecao.selecionar(pesquisa).selecionados())).containsExactly(1, 2, 5);
    }

    @Test
    void scoresTitleNumberSummaryAndDecision() {
        var doc = new Summary(1L, "STJ", "decisoes", null, "Título", null, "Tema 1061", null, "Ementa sobre juros",
                "Recurso provido", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThat(SelecaoResultadosRelevantes.pontuacao(doc, List.of("1061", "juros", "provido", "ausente"))).isEqualTo(3);
    }

    private static PesquisaAtiva pesquisa(String termo, String ordenacao, String modo, String... titulos) {
        var filtros = new PesquisaAtivaRequest(modo, null, null, null, null, null, null, null, ordenacao, null, null, null);
        return new PesquisaAtiva(termo, filtros, resultados(titulos), titulos.length);
    }

    private static List<ResultadoPesquisa> resultados(String... titulos) {
        var resultados = new ArrayList<ResultadoPesquisa>();
        for (int i = 0; i < titulos.length; i++) {
            long id = i + 1;
            var doc = new Summary(id, "STJ", "decisoes", null, titulos[i], null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null, null, null);
            resultados.add(new ResultadoPesquisa(i + 1, new DocumentoReferencia("decisoes", id), doc));
        }
        return resultados;
    }

    private static List<Integer> posicoes(List<ResultadoPesquisa> resultados) {
        return resultados.stream().map(ResultadoPesquisa::posicao).toList();
    }
}
