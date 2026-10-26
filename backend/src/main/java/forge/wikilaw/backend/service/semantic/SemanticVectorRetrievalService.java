package forge.wikilaw.backend.service.semantic;

import forge.wikilaw.backend.config.SemanticSearchProperties;
import forge.wikilaw.backend.entity.DocumentoSemantico;
import forge.wikilaw.backend.integration.embedding.EmbeddingProvider;
import forge.wikilaw.backend.repository.EmbeddingTrechoRepository;
import forge.wikilaw.backend.repository.EmbeddingTrechoRepository.SimilarChunk;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Recupera os documentos mais proximos de um vetor de consulta. Varios trechos do mesmo documento
 * podem ser parecidos; o documento vale pelo melhor trecho e desempata pela quantidade de trechos.
 */
@Service
public class SemanticVectorRetrievalService {
    private final EmbeddingTrechoRepository embeddings;
    private final EmbeddingProvider provider;
    private final SemanticSearchProperties properties;

    public SemanticVectorRetrievalService(EmbeddingTrechoRepository embeddings, EmbeddingProvider provider,
            SemanticSearchProperties properties) {
        this.embeddings = embeddings;
        this.provider = provider;
        this.properties = properties;
    }

    public List<SimilarDocument> recuperar(float[] queryVector,
            Collection<DocumentoSemantico.Categoria> categories, int limit) {
        if (limit < 1 || limit > properties.getMaxResults())
            throw new IllegalArgumentException("limite deve estar entre 1 e " + properties.getMaxResults());
        var selected = categories == null || categories.isEmpty()
                ? EnumSet.allOf(DocumentoSemantico.Categoria.class)
                : EnumSet.copyOf(categories);

        List<SimilarChunk> chunks = embeddings.findSimilares(queryVector, provider.model(), selected,
                limit * properties.getCandidateMultiplier());

        Map<Long, List<SimilarChunk>> byDocument = new LinkedHashMap<>();
        chunks.forEach(chunk -> byDocument.computeIfAbsent(chunk.idDocumentoSemantico(), id -> new ArrayList<>())
                .add(chunk));

        return byDocument.values().stream()
                .map(this::toDocument)
                .sorted(Comparator.comparingDouble(SimilarDocument::similaridade).reversed()
                        .thenComparing(Comparator.comparingInt(SimilarDocument::trechosSemelhantes).reversed())
                        .thenComparing(SimilarDocument::idDocumentoSemantico))
                .limit(limit)
                .toList();
    }

    private SimilarDocument toDocument(List<SimilarChunk> chunks) {
        SimilarChunk best = chunks.stream().max(Comparator.comparingDouble(SimilarChunk::similaridade)).orElseThrow();
        return new SimilarDocument(best.idDocumentoSemantico(), best.categoria(), best.idDecisao(),
                best.idPrecedente(), best.idDocumentoDoutrinario(), best.similaridade(), chunks.size(),
                best.secao(), best.texto());
    }

    /** Documento recuperado com a similaridade do melhor trecho e o trecho que justifica o resultado. */
    public record SimilarDocument(Long idDocumentoSemantico, DocumentoSemantico.Categoria categoria,
            Long idDecisao, Long idPrecedente, Long idDocumentoDoutrinario,
            double similaridade, int trechosSemelhantes, String secaoRelevante, String trechoRelevante) {}
}
