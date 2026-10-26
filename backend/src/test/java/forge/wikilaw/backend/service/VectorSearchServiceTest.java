package forge.wikilaw.backend.service;

import forge.wikilaw.backend.dto.SimilaridadeRequest;
import forge.wikilaw.backend.service.semantic.*;
import forge.wikilaw.backend.service.search.SearchTermProcessor;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VectorSearchServiceTest {
    final VectorStore store=mock(VectorStore.class);
    final EmbeddingClient embeddings=mock(EmbeddingClient.class);
    final DocumentQueryService documents=mock(DocumentQueryService.class);
    final SearchTermProcessor terms=new SearchTermProcessor();
    final SearchContextService context=new SearchContextService(terms,new LegalTextNormalizer());
    VectorSearchService service(boolean enabled) {
        return new VectorSearchService(enabled,store,embeddings,new LegalTextChunker(60,8),documents,context,terms);
    }
    @Test void disabledSearchNeverCallsTheDatabaseOrModel() {
        assertThat(service(false).status().habilitada()).isFalse();
        assertThatThrownBy(()->service(false).search(new SimilaridadeRequest("caso",null,10))).hasMessageContaining("503");
        verifyNoInteractions(store,embeddings);
    }
    @Test void emptyIndexDoesNotPretendToRunSimilarity() {
        when(store.counts()).thenReturn(new VectorStore.Counts(0,20));
        assertThatThrownBy(()->service(true).search(new SimilaridadeRequest("caso",null,10))).hasMessageContaining("indexados");
        verifyNoInteractions(embeddings);
    }
    @Test void sendsEveryDocumentChunkIncludingTheEndToTheModel() {
        when(store.counts()).thenReturn(new VectorStore.Counts(10,4));
        when(embeddings.embed(anyList(),eq("query"))).thenReturn(List.of(new float[384]));
        when(store.nearest(any(),anyInt())).thenReturn(List.of());
        when(store.lexical(anyString(),anyInt())).thenReturn(List.of());
        String text="direito consumidor reparação ".repeat(200)+"MARCADORFINAL";
        service(true).search(new SimilaridadeRequest("cirurgia",text,10));
        var captor=org.mockito.ArgumentCaptor.forClass(List.class);
        verify(embeddings).embed(captor.capture(),eq("query"));
        assertThat(captor.getValue().toString()).contains("MARCADORFINAL","cirurgia");
        assertThat(captor.getValue().size()).isGreaterThan(2);
    }
    @Test void indexerDoesNotRequestEmbeddingsAgainForCompletedChunks() {
        when(store.lockPending(8)).thenReturn(List.of());
        when(embeddings.embed(List.of(),"passage")).thenReturn(List.of());
        assertThat(new VectorIndexService(store,embeddings).indexBatch()).isZero();
        verify(store,never()).save(any(),any());
    }
}
