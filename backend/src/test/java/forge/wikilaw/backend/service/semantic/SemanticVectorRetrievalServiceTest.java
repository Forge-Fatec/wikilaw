package forge.wikilaw.backend.service.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import forge.wikilaw.backend.config.SemanticSearchProperties;
import forge.wikilaw.backend.entity.DocumentoSemantico.Categoria;
import forge.wikilaw.backend.integration.embedding.EmbeddingProvider;
import forge.wikilaw.backend.repository.EmbeddingTrechoRepository;
import forge.wikilaw.backend.repository.EmbeddingTrechoRepository.SimilarChunk;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SemanticVectorRetrievalServiceTest {
    private final float[] query = new float[] {1f};
    private EmbeddingTrechoRepository embeddings;
    private SemanticVectorRetrievalService service;

    @BeforeEach
    void setUp() {
        embeddings = mock(EmbeddingTrechoRepository.class);
        EmbeddingProvider provider = mock(EmbeddingProvider.class);
        when(provider.model()).thenReturn("voyage-4");
        service = new SemanticVectorRetrievalService(embeddings, provider, new SemanticSearchProperties());
    }

    @Test
    void groupsChunksByDocumentKeepingTheBestChunkAsJustification() {
        when(embeddings.findSimilares(eq(query), eq("voyage-4"), any(), anyInt())).thenReturn(List.of(
                chunk(10L, 1L, "EMENTA", "usucapiao extraordinaria", 0.91),
                chunk(20L, 2L, "TESE", "posse mansa e pacifica", 0.88),
                chunk(11L, 1L, "INTEIRO_TEOR", "requisitos da posse", 0.80)));

        var result = service.recuperar(query, null, 10);

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().idDocumentoSemantico()).isEqualTo(1L);
        assertThat(result.getFirst().similaridade()).isEqualTo(0.91);
        assertThat(result.getFirst().trechosSemelhantes()).isEqualTo(2);
        assertThat(result.getFirst().secaoRelevante()).isEqualTo("EMENTA");
        assertThat(result.getFirst().trechoRelevante()).isEqualTo("usucapiao extraordinaria");
        assertThat(result.getFirst().idDecisao()).isEqualTo(100L);
        assertThat(result.get(1).idDocumentoSemantico()).isEqualTo(2L);
    }

    @Test
    void breaksTiesByNumberOfSimilarChunks() {
        when(embeddings.findSimilares(any(), any(), any(), anyInt())).thenReturn(List.of(
                chunk(10L, 1L, "EMENTA", "a", 0.85),
                chunk(20L, 2L, "EMENTA", "b", 0.85),
                chunk(21L, 2L, "DECISAO", "c", 0.70)));

        var result = service.recuperar(query, null, 10);

        assertThat(result).extracting(SemanticVectorRetrievalService.SimilarDocument::idDocumentoSemantico)
                .containsExactly(2L, 1L);
    }

    @Test
    void fetchesExtraCandidatesAndTrimsToTheRequestedLimit() {
        when(embeddings.findSimilares(any(), any(), any(), anyInt())).thenReturn(List.of(
                chunk(10L, 1L, "EMENTA", "a", 0.9),
                chunk(20L, 2L, "EMENTA", "b", 0.8),
                chunk(30L, 3L, "EMENTA", "c", 0.7)));

        var result = service.recuperar(query, EnumSet.of(Categoria.PRECEDENTE), 2);

        assertThat(result).hasSize(2);
        verify(embeddings).findSimilares(query, "voyage-4", EnumSet.of(Categoria.PRECEDENTE), 10);
    }

    @Test
    void searchesEveryCategoryWhenNoneIsRequested() {
        when(embeddings.findSimilares(any(), any(), any(), anyInt())).thenReturn(List.of());

        assertThat(service.recuperar(query, List.of(), 5)).isEmpty();
        verify(embeddings).findSimilares(query, "voyage-4", EnumSet.allOf(Categoria.class), 25);
    }

    @Test
    void rejectsLimitsOutsideTheConfiguredRange() {
        assertThatThrownBy(() -> service.recuperar(query, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recuperar(query, null, 51))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50");
    }

    private SimilarChunk chunk(Long chunkId, Long documentId, String section, String text, double similarity) {
        return new SimilarChunk(chunkId, documentId, Categoria.JURISPRUDENCIA, documentId * 100, null, null,
                section, text, similarity);
    }
}
