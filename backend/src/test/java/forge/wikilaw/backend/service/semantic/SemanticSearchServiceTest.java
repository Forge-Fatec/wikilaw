package forge.wikilaw.backend.service.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import forge.wikilaw.backend.entity.DocumentoSemantico.Categoria;
import forge.wikilaw.backend.service.DocumentQueryService;
import forge.wikilaw.backend.service.DocumentQueryService.Summary;
import forge.wikilaw.backend.service.semantic.SemanticCaseRepresentationService.CaseRepresentation;
import forge.wikilaw.backend.service.semantic.SemanticVectorRetrievalService.SimilarDocument;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SemanticSearchServiceTest {
    private final float[] vector = {1f};
    private final SemanticCaseQuery query = new SemanticCaseQuery(null, "fatos do caso", "fundamentos", "pedidos");
    private SemanticVectorRetrievalService retrieval;
    private DocumentQueryService documents;
    private SemanticSearchService service;

    @BeforeEach
    void setUp() {
        var representation = mock(SemanticCaseRepresentationService.class);
        retrieval = mock(SemanticVectorRetrievalService.class);
        documents = mock(DocumentQueryService.class);
        when(representation.representar(query)).thenReturn(new CaseRepresentation("texto", vector, "voyage-4"));
        service = new SemanticSearchService(representation, retrieval, documents);
    }

    @Test
    void returnsSummariesInSimilarityOrderWithTheJustifyingChunk() {
        when(retrieval.recuperar(eq(vector), any(), eq(10))).thenReturn(List.of(
                similar(1L, Categoria.PRECEDENTE, 7L, 0.59, "QUESTAO_JURIDICA", "emprestimo consignado"),
                similar(2L, Categoria.JURISPRUDENCIA, 3L, 0.54, "EMENTA", "desconto indevido"),
                similar(3L, Categoria.PRECEDENTE, 8L, 0.52, "TESE", "devolucao em dobro")));
        when(documents.summariesByIds("precedentes", List.of(7L, 8L)))
                .thenReturn(List.of(summary(8L, "precedentes"), summary(7L, "precedentes")));
        when(documents.summariesByIds("decisoes", List.of(3L))).thenReturn(List.of(summary(3L, "decisoes")));

        var result = service.buscar(query, null, 10);

        assertThat(result.modelo()).isEqualTo("voyage-4");
        assertThat(result.resultados()).extracting(item -> item.documento().id()).containsExactly(7L, 3L, 8L);
        var first = result.resultados().getFirst();
        assertThat(first.similaridade()).isEqualTo(0.59);
        assertThat(first.documento().categoria()).isEqualTo("precedentes");
        assertThat(first.trechoRelevante().secao()).isEqualTo("QUESTAO_JURIDICA");
        assertThat(first.trechoRelevante().texto()).isEqualTo("emprestimo consignado");
    }

    @Test
    void dropsDocumentsThatAreNoLongerDisplayable() {
        when(retrieval.recuperar(any(), any(), anyInt())).thenReturn(List.of(
                similar(1L, Categoria.DOUTRINA, 5L, 0.7, "RESUMO", "a"),
                similar(2L, Categoria.DOUTRINA, 6L, 0.6, "RESUMO", "b")));
        when(documents.summariesByIds("doutrina", List.of(5L, 6L))).thenReturn(List.of(summary(6L, "doutrina")));

        var result = service.buscar(query, List.of(Categoria.DOUTRINA), 5);

        assertThat(result.resultados()).extracting(item -> item.documento().id()).containsExactly(6L);
    }

    @Test
    void returnsNoResultsWithoutLoadingSummariesWhenNothingIsSimilar() {
        when(retrieval.recuperar(any(), any(), anyInt())).thenReturn(List.of());

        assertThat(service.buscar(query, null, 5).resultados()).isEmpty();
        verify(documents, never()).summariesByIds(any(), anyList());
    }

    @Test
    void translatesCategoriesUsedByTheDocumentsApi() {
        assertThat(SemanticSearchService.fromApiCategory("decisoes")).isEqualTo(Categoria.JURISPRUDENCIA);
        assertThat(SemanticSearchService.apiCategory(Categoria.PRECEDENTE)).isEqualTo("precedentes");
        assertThatThrownBy(() -> SemanticSearchService.fromApiCategory("leis"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SimilarDocument similar(Long semanticId, Categoria category, Long sourceId, double similarity,
            String section, String text) {
        return new SimilarDocument(semanticId, category,
                category == Categoria.JURISPRUDENCIA ? sourceId : null,
                category == Categoria.PRECEDENTE ? sourceId : null,
                category == Categoria.DOUTRINA ? sourceId : null,
                similarity, 1, section, text);
    }

    private Summary summary(Long id, String category) {
        return new Summary(id, "STJ", category, "ext-" + id, "Titulo " + id, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
