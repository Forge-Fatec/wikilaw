package forge.wikilaw.backend.service.pesquisa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import forge.wikilaw.backend.dto.AnaliseJuridicaRequest;
import forge.wikilaw.backend.dto.PesquisaAtivaRequest;
import forge.wikilaw.backend.dto.PesquisaPdfRequest.DocumentoReferencia;
import forge.wikilaw.backend.exception.ContextoInsuficienteException;
import forge.wikilaw.backend.service.DocumentQueryService;
import forge.wikilaw.backend.service.DocumentQueryService.Summary;
import forge.wikilaw.backend.service.pesquisa.PesquisaAtiva.ResultadoPesquisa;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PesquisaAtivaServiceTest {

    private final DocumentQueryService documents = mock(DocumentQueryService.class);
    private final PesquisaAtivaService service = new PesquisaAtivaService(documents);

    @Test
    void recoversResultsInTheDisplayedOrderWithTheirPositions() {
        when(documents.summariesByIds("decisoes", List.of(7L, 2L))).thenReturn(List.of(summary(2L, "decisoes"), summary(7L, "decisoes")));
        when(documents.summariesByIds("precedentes", List.of(3L))).thenReturn(List.of(summary(3L, "precedentes")));
        var refs = List.of(new DocumentoReferencia("decisoes", 7L), new DocumentoReferencia("precedentes", 3L),
                new DocumentoReferencia("decisoes", 2L));

        PesquisaAtiva pesquisa = service.recuperar(new AnaliseJuridicaRequest("dano moral", pesquisa(List.of(), 40), refs));

        assertThat(pesquisa.termo()).isEqualTo("dano moral");
        assertThat(pesquisa.totalResultados()).isEqualTo(40);
        assertThat(pesquisa.resultados()).extracting(ResultadoPesquisa::posicao).containsExactly(1, 2, 3);
        assertThat(pesquisa.resultados()).extracting(ResultadoPesquisa::referencia).isEqualTo(refs);
        assertThat(pesquisa.resultados()).extracting(r -> r.documento().id()).containsExactly(7L, 3L, 2L);
    }

    @Test
    void countsAtLeastTheSentResultsWhenTheTotalIsMissing() {
        when(documents.summariesByIds("decisoes", List.of(1L))).thenReturn(List.of(summary(1L, "decisoes")));

        PesquisaAtiva pesquisa = service.recuperar(new AnaliseJuridicaRequest("x",
                List.of(new DocumentoReferencia("decisoes", 1L))));

        assertThat(pesquisa.totalResultados()).isEqualTo(1);
        assertThat(pesquisa.filtros()).isEqualTo(PesquisaAtivaRequest.vazia());
    }

    @Test
    void reportsAnActiveSearchWithoutResults() {
        assertThatThrownBy(() -> service.recuperar(new AnaliseJuridicaRequest("x", List.of())))
                .isInstanceOf(ContextoInsuficienteException.class);
        verify(documents, never()).summariesByIds(anyString(), any());
    }

    @Test
    void rejectsResultsOutsideTheSearchedCategories() {
        var request = new AnaliseJuridicaRequest("x", pesquisa(List.of("decisoes"), null),
                List.of(new DocumentoReferencia("doutrina", 1L)));

        assertThatThrownBy(() -> service.recuperar(request))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("categorias da pesquisa ativa");
        verify(documents, never()).summariesByIds(anyString(), any());
    }

    @Test
    void rejectsRepeatedResults() {
        var ref = new DocumentoReferencia("decisoes", 1L);
        assertThatThrownBy(() -> service.recuperar(new AnaliseJuridicaRequest("x", List.of(ref, ref))))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("repetidos");
    }

    @Test
    void reportsResultsThatLeftTheCollection() {
        when(documents.summariesByIds("decisoes", List.of(1L, 2L))).thenReturn(List.of(summary(1L, "decisoes")));

        assertThatThrownBy(() -> service.recuperar(new AnaliseJuridicaRequest("x", List.of(
                new DocumentoReferencia("decisoes", 1L), new DocumentoReferencia("decisoes", 2L)))))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("não está mais disponível");
    }

    private static PesquisaAtivaRequest pesquisa(List<String> categorias, Integer total) {
        return new PesquisaAtivaRequest("textual", null, null, categorias, null, null, null, null, "mais-recentes",
                null, null, total);
    }

    private static Summary summary(Long id, String categoria) {
        return new Summary(id, "STJ", categoria, null, "Doc " + id, null, null, null, "Ementa " + id, null, null,
                "STJ", null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
