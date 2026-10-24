package forge.wikilaw.backend.service.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import forge.wikilaw.backend.entity.DocumentoSemantico;
import forge.wikilaw.backend.entity.TrechoSemantico;
import forge.wikilaw.backend.exception.EmbeddingProviderException;
import forge.wikilaw.backend.integration.embedding.EmbeddingProperties;
import forge.wikilaw.backend.integration.embedding.EmbeddingProvider;
import forge.wikilaw.backend.repository.DocumentoSemanticoRepository;
import forge.wikilaw.backend.repository.EmbeddingTrechoRepository;
import forge.wikilaw.backend.repository.EmbeddingTrechoRepository.TrechoEmbedding;
import forge.wikilaw.backend.repository.TrechoSemanticoRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.transaction.PlatformTransactionManager;

class SemanticEmbeddingServiceTest {
    private static final String MODEL = "voyage-4";

    private DocumentoSemanticoRepository documents;
    private TrechoSemanticoRepository chunks;
    private EmbeddingTrechoRepository embeddings;
    private EmbeddingProvider provider;
    private SemanticEmbeddingService service;
    private final Map<Long, DocumentoSemantico> stored = new HashMap<>();

    @BeforeEach
    void setUp() {
        documents = mock(DocumentoSemanticoRepository.class);
        chunks = mock(TrechoSemanticoRepository.class);
        embeddings = mock(EmbeddingTrechoRepository.class);
        provider = mock(EmbeddingProvider.class);
        when(provider.model()).thenReturn(MODEL);
        when(documents.findById(any())).thenAnswer(call -> Optional.ofNullable(stored.get(call.<Long>getArgument(0))));
        when(documents.findByIdForUpdate(any()))
                .thenAnswer(call -> Optional.ofNullable(stored.get(call.<Long>getArgument(0))));
        service = new SemanticEmbeddingService(documents, chunks, embeddings, provider,
                new EmbeddingProperties(), mock(PlatformTransactionManager.class));
    }

    @Test
    void embedsChunksOfSeveralDocumentsInOneCallAndMarksThemReady() {
        DocumentoSemantico first = document(1L, 3);
        DocumentoSemantico second = document(2L, 0);
        pending(first, second);
        chunks(1L, chunk(10L, "Secao: EMENTA\nTexto:\nnegativacao indevida"), chunk(11L, "Secao: DECISAO\nTexto:\nprocedente"));
        chunks(2L, chunk(20L, "Secao: TESE\nTexto:\nresponsabilidade objetiva"));
        when(provider.embedDocuments(anyList())).thenReturn(List.of(vector(1), vector(2), vector(3)));

        var result = service.processBatch();

        assertThat(result).isEqualTo(new SemanticEmbeddingService.BatchResult(2, 2, 0));
        verify(provider).embedDocuments(List.of("Secao: EMENTA\nTexto:\nnegativacao indevida",
                "Secao: DECISAO\nTexto:\nprocedente", "Secao: TESE\nTexto:\nresponsabilidade objetiva"));
        assertThat(savedChunkIds(2)).containsExactly(List.of(10L, 11L), List.of(20L));
        verify(embeddings).deleteByDocumento(1L);
        for (DocumentoSemantico document : List.of(first, second)) {
            assertThat(document.getStatusEmbedding()).isEqualTo(DocumentoSemantico.StatusEmbedding.PRONTO);
            assertThat(document.getModeloEmbedding()).isEqualTo(MODEL);
            assertThat(document.getTentativasEmbedding()).isZero();
            assertThat(document.getErroEmbedding()).isNull();
            assertThat(document.getEmbeddingAtualizadoEm()).isNotNull();
        }
    }

    @Test
    void doesNothingWhenNoDocumentIsPending() {
        pending();

        assertThat(service.processBatch()).isEqualTo(new SemanticEmbeddingService.BatchResult(0, 0, 0));
        verify(provider, never()).embedDocuments(anyList());
    }

    @Test
    void recordsProviderFailureAndCountsTheAttempt() {
        DocumentoSemantico document = document(1L, 1);
        pending(document);
        chunks(1L, chunk(10L, "texto"));
        when(provider.embedDocuments(anyList()))
                .thenThrow(new EmbeddingProviderException("Provedor de embeddings respondeu com HTTP 500", 500));

        var result = service.processBatch();

        assertThat(result).isEqualTo(new SemanticEmbeddingService.BatchResult(1, 0, 1));
        assertThat(document.getStatusEmbedding()).isEqualTo(DocumentoSemantico.StatusEmbedding.ERRO);
        assertThat(document.getTentativasEmbedding()).isEqualTo(2);
        assertThat(document.getErroEmbedding()).contains("HTTP 500");
        verify(embeddings, never()).saveAll(anyString(), anyList());
    }

    @Test
    void rateLimitDoesNotConsumeAnAttempt() {
        DocumentoSemantico document = document(1L, 1);
        pending(document);
        chunks(1L, chunk(10L, "texto"));
        when(provider.embedDocuments(anyList()))
                .thenThrow(new EmbeddingProviderException("Provedor de embeddings respondeu com HTTP 429", 429));

        service.processBatch();

        assertThat(document.getStatusEmbedding()).isEqualTo(DocumentoSemantico.StatusEmbedding.ERRO);
        assertThat(document.getTentativasEmbedding()).isEqualTo(1);
    }

    @Test
    void discardsVectorsWhenTheDocumentWasPreparedAgainMeanwhile() {
        DocumentoSemantico document = document(1L, 0);
        pending(document);
        chunks(1L, chunk(10L, "texto antigo"));
        when(provider.embedDocuments(anyList())).thenAnswer(call -> {
            document.setHashConteudo("hash-novo");
            document.marcarEmbeddingPendente();
            return List.of(vector(1));
        });

        var result = service.processBatch();

        assertThat(result).isEqualTo(new SemanticEmbeddingService.BatchResult(1, 0, 0));
        verify(embeddings, never()).saveAll(anyString(), anyList());
        assertThat(document.getStatusEmbedding()).isEqualTo(DocumentoSemantico.StatusEmbedding.PENDENTE);
    }

    @Test
    void isolatesPersistenceFailuresPerDocument() {
        DocumentoSemantico broken = document(1L, 0);
        DocumentoSemantico healthy = document(2L, 0);
        pending(broken, healthy);
        chunks(1L, chunk(10L, "a"));
        chunks(2L, chunk(20L, "b"));
        when(provider.embedDocuments(anyList())).thenReturn(List.of(vector(1), vector(2)));
        doThrow(new IllegalArgumentException("Embedding deve ter 1024 dimensoes, recebeu 3"))
                .when(embeddings).saveAll(eq(MODEL), ArgumentMatchers.<List<TrechoEmbedding>>argThat(
                        rows -> rows != null && rows.getFirst().idTrecho() == 10L));

        var result = service.processBatch();

        assertThat(result).isEqualTo(new SemanticEmbeddingService.BatchResult(2, 1, 1));
        assertThat(broken.getStatusEmbedding()).isEqualTo(DocumentoSemantico.StatusEmbedding.ERRO);
        assertThat(broken.getErroEmbedding()).contains("1024 dimensoes");
        assertThat(healthy.getStatusEmbedding()).isEqualTo(DocumentoSemantico.StatusEmbedding.PRONTO);
    }

    private void pending(DocumentoSemantico... pending) {
        for (DocumentoSemantico document : pending) stored.put(document.getId(), document);
        when(documents.findEmbeddingPendentes(eq(MODEL), anyInt(), any(OffsetDateTime.class),
                any(OffsetDateTime.class), anyInt()))
                .thenReturn(java.util.Arrays.stream(pending).map(DocumentoSemantico::getId).toList());
    }

    private void chunks(Long documentId, TrechoSemantico... values) {
        for (TrechoSemantico chunk : values) chunk.setIdDocumentoSemantico(documentId);
        when(chunks.findByIdDocumentoSemanticoOrderByOrdem(documentId)).thenReturn(List.of(values));
    }

    private List<List<Long>> savedChunkIds(int calls) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TrechoEmbedding>> captor = ArgumentCaptor.forClass(List.class);
        verify(embeddings, org.mockito.Mockito.times(calls)).saveAll(eq(MODEL), captor.capture());
        var ids = new ArrayList<List<Long>>();
        captor.getAllValues().forEach(rows -> ids.add(rows.stream().map(TrechoEmbedding::idTrecho).toList()));
        return ids;
    }

    private DocumentoSemantico document(Long id, int attempts) {
        var document = new DocumentoSemantico();
        document.setId(id);
        document.setStatus(DocumentoSemantico.Status.PRONTO);
        document.setHashConteudo("hash-" + id);
        document.setTentativasEmbedding(attempts);
        return document;
    }

    private TrechoSemantico chunk(Long id, String embeddingText) {
        var chunk = new TrechoSemantico();
        chunk.setId(id);
        chunk.setTextoParaEmbedding(embeddingText);
        chunk.setHashTexto("h" + id);
        return chunk;
    }

    private float[] vector(float value) {
        return new float[] {value};
    }
}
