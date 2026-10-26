package forge.wikilaw.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import forge.wikilaw.backend.entity.DocumentoSemantico.Categoria;
import forge.wikilaw.backend.exception.EmbeddingProviderException;
import forge.wikilaw.backend.exception.SemanticQueryException;
import forge.wikilaw.backend.service.DocumentQueryService.Summary;
import forge.wikilaw.backend.service.semantic.SemanticCaseQuery;
import forge.wikilaw.backend.service.semantic.SemanticSearchService;
import forge.wikilaw.backend.service.semantic.SemanticSearchService.RelevantChunk;
import forge.wikilaw.backend.service.semantic.SemanticSearchService.SemanticSearchItem;
import forge.wikilaw.backend.service.semantic.SemanticSearchService.SemanticSearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class SemanticSearchControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SemanticSearchService service;

    @Test
    void searchesWithFactsLegalGroundsAndRequests() throws Exception {
        var query = new SemanticCaseQuery(null, "Desconto de emprestimo nao contratado.", "CDC, art. 14.",
                "Devolucao em dobro.");
        when(service.buscar(eq(query), eq(List.of(Categoria.PRECEDENTE, Categoria.JURISPRUDENCIA)), eq(5)))
                .thenReturn(new SemanticSearchResult("voyage-4", List.of(new SemanticSearchItem(
                summary(), 0.588, 78, 2, new RelevantChunk("QUESTAO_JURIDICA", "Teses do IRDR do TJMA"),
                "A questão jurídica deste documento se aproxima do caso descrito (2 trechos semelhantes)."))));

        mvc.perform(post("/api/pesquisas/semantica").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fatos":"Desconto de emprestimo nao contratado.","fundamentos":"CDC, art. 14.",
                         "pedidos":"Devolucao em dobro.","categorias":["precedentes","decisoes"],"limite":5}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelo").value("voyage-4"))
                .andExpect(jsonPath("$.resultados[0].documento.id").value(42))
                .andExpect(jsonPath("$.resultados[0].documento.categoria").value("precedentes"))
                .andExpect(jsonPath("$.resultados[0].similaridade").value(0.588))
                .andExpect(jsonPath("$.resultados[0].trechosSemelhantes").value(2))
                .andExpect(jsonPath("$.resultados[0].trechoRelevante.secao").value("QUESTAO_JURIDICA"));
    }

    @Test
    void usesEveryCategoryAndTenResultsByDefault() throws Exception {
        when(service.buscar(any(), any(), anyInt())).thenReturn(new SemanticSearchResult("voyage-4", List.of()));

        mvc.perform(post("/api/pesquisas/semantica").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\":\"Moro no imovel ha quinze anos sem oposicao.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados").isEmpty());
        verify(service).buscar(SemanticCaseQuery.livre("Moro no imovel ha quinze anos sem oposicao."), List.of(), 10);
    }

    @Test
    void rejectsUnknownCategoriesAndLimitsAboveFifty() throws Exception {
        mvc.perform(post("/api/pesquisas/semantica").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\":\"caso\",\"categorias\":[\"leis\"],\"limite\":51}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Requisição inválida"));
        verify(service, never()).buscar(any(), any(), anyInt());
    }

    @Test
    void reportsInvalidCaseDescriptionsAsBadRequest() throws Exception {
        when(service.buscar(any(), any(), anyInt()))
                .thenThrow(new SemanticQueryException("Descreva o caso com pelo menos 20 caracteres"));

        mvc.perform(post("/api/pesquisas/semantica").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\":\"curto\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Consulta semântica inválida"))
                .andExpect(jsonPath("$.details[0]").value("Descreva o caso com pelo menos 20 caracteres"));
    }

    @Test
    void reportsProviderProblemsAsUnavailable() throws Exception {
        when(service.buscar(any(), any(), anyInt()))
                .thenThrow(new EmbeddingProviderException("Geracao de embeddings desabilitada", 0));

        mvc.perform(post("/api/pesquisas/semantica").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\":\"Moro no imovel ha quinze anos sem oposicao.\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Busca semântica indisponível"));
    }

    @Test
    void reportsProviderRateLimitAsTooManyRequests() throws Exception {
        when(service.buscar(any(), any(), anyInt()))
                .thenThrow(new EmbeddingProviderException("Provedor de embeddings respondeu com HTTP 429", 429));

        mvc.perform(post("/api/pesquisas/semantica").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\":\"Moro no imovel ha quinze anos sem oposicao.\"}"))
                .andExpect(status().isTooManyRequests());
    }

    private Summary summary() {
        return new Summary(42L, "STJ", "precedentes", "tema-1", "IRDR emprestimo consignado", null, null, null,
                null, null, null, "TJMA", null, null, null, null, null, null, null, null, null, null, null, null,
                null);
    }
}
