package forge.wikilaw.backend.service.semantic;

import forge.wikilaw.backend.integration.embedding.EmbeddingProperties;
import forge.wikilaw.backend.repository.EmbeddingTrechoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "wikilaw.semantic.embedding.enabled", havingValue = "true")
public class SemanticEmbeddingWorker {
    private static final Logger log = LoggerFactory.getLogger(SemanticEmbeddingWorker.class);
    private final SemanticEmbeddingService embeddings;

    public SemanticEmbeddingWorker(SemanticEmbeddingService embeddings, EmbeddingProperties properties) {
        if (properties.getModel().isBlank() || properties.getBaseUrl().isBlank())
            throw new IllegalStateException(
                    "Embeddings habilitados sem WIKILAW_EMBEDDING_BASE_URL e WIKILAW_EMBEDDING_MODEL");
        if (properties.getDimensions() != EmbeddingTrechoRepository.DIMENSIONS)
            throw new IllegalStateException("WIKILAW_EMBEDDING_DIMENSIONS deve ser "
                    + EmbeddingTrechoRepository.DIMENSIONS + " para a coluna embedding_trecho.vetor");
        this.embeddings = embeddings;
    }

    @Scheduled(initialDelayString = "${wikilaw.semantic.embedding.worker.initial-delay:10000}",
            fixedDelayString = "${wikilaw.semantic.embedding.worker.poll-delay:5000}")
    public void process() {
        try {
            var result = embeddings.processBatch();
            if (result.claimed() > 0)
                log.info("Embeddings gerados: {} documentos prontos, {} com falha",
                        result.embedded(), result.failed());
        } catch (RuntimeException exception) {
            log.error("Falha no lote de geracao de embeddings", exception);
        }
    }
}
