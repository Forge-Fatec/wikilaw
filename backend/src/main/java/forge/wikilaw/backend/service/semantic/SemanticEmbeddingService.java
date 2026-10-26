package forge.wikilaw.backend.service.semantic;

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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gera os vetores em tres passos para nao segurar lock do banco durante a chamada HTTP:
 * reserva os documentos (PROCESSANDO), chama o provedor fora de transacao e grava o resultado.
 * Os trechos de varios documentos vao juntos ao provedor para economizar requisicoes.
 */
@Service
public class SemanticEmbeddingService {
    private static final Logger log = LoggerFactory.getLogger(SemanticEmbeddingService.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private final DocumentoSemanticoRepository documents;
    private final TrechoSemanticoRepository chunks;
    private final EmbeddingTrechoRepository embeddings;
    private final EmbeddingProvider provider;
    private final EmbeddingProperties properties;
    private final TransactionTemplate transaction;

    public SemanticEmbeddingService(DocumentoSemanticoRepository documents, TrechoSemanticoRepository chunks,
            EmbeddingTrechoRepository embeddings, EmbeddingProvider provider, EmbeddingProperties properties,
            PlatformTransactionManager transactionManager) {
        this.documents = documents;
        this.chunks = chunks;
        this.embeddings = embeddings;
        this.provider = provider;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public BatchResult processBatch() {
        List<Claim> claims = transaction.execute(status -> claim());
        if (claims == null || claims.isEmpty()) return new BatchResult(0, 0, 0);

        List<String> texts = new ArrayList<>();
        claims.forEach(claim -> claim.chunks().forEach(chunk -> texts.add(chunk.getTextoParaEmbedding())));
        List<float[]> vectors;
        try {
            vectors = provider.embedDocuments(texts);
            if (vectors.size() != texts.size())
                throw new EmbeddingProviderException("Provedor retornou " + vectors.size()
                        + " vetores para " + texts.size() + " trechos", 0);
        } catch (RuntimeException exception) {
            log.warn("Falha ao gerar embeddings de {} documentos: {}", claims.size(), exception.getMessage());
            claims.forEach(claim -> fail(claim, exception));
            return new BatchResult(claims.size(), 0, claims.size());
        }

        int embedded = 0;
        int failed = 0;
        int offset = 0;
        for (Claim claim : claims) {
            List<float[]> documentVectors = vectors.subList(offset, offset + claim.chunks().size());
            offset += claim.chunks().size();
            try {
                if (Boolean.TRUE.equals(transaction.execute(status -> complete(claim, documentVectors)))) embedded++;
            } catch (RuntimeException exception) {
                log.warn("Falha ao gravar embeddings do documento {}: {}", claim.documentId(), exception.getMessage());
                fail(claim, exception);
                failed++;
            }
        }
        return new BatchResult(claims.size(), embedded, failed);
    }

    private List<Claim> claim() {
        OffsetDateTime now = now();
        EmbeddingProperties.Worker worker = properties.getWorker();
        List<Long> ids = documents.findEmbeddingPendentes(provider.model(), worker.getMaxAttempts(),
                now.minus(worker.getRetryBackoff()), now.minus(worker.getProcessingTimeout()),
                worker.getDocumentsPerBatch());
        var claims = new ArrayList<Claim>();
        for (Long id : ids) {
            documents.findById(id).ifPresent(document -> {
                document.setStatusEmbedding(DocumentoSemantico.StatusEmbedding.PROCESSANDO);
                document.setEmbeddingAtualizadoEm(now);
                documents.save(document);
                claims.add(new Claim(id, document.getHashConteudo(),
                        chunks.findByIdDocumentoSemanticoOrderByOrdem(id)));
            });
        }
        return claims;
    }

    private boolean complete(Claim claim, List<float[]> vectors) {
        DocumentoSemantico document = documents.findByIdForUpdate(claim.documentId()).orElse(null);
        if (!stillReserved(document, claim)) {
            log.info("Documento {} mudou durante a geracao de embeddings; vetores descartados", claim.documentId());
            return false;
        }
        var rows = new ArrayList<TrechoEmbedding>(vectors.size());
        for (int i = 0; i < vectors.size(); i++) {
            TrechoSemantico chunk = claim.chunks().get(i);
            rows.add(new TrechoEmbedding(chunk.getId(), chunk.getHashTexto(), vectors.get(i)));
        }
        embeddings.deleteByDocumento(claim.documentId());
        embeddings.saveAll(provider.model(), rows);
        document.setStatusEmbedding(DocumentoSemantico.StatusEmbedding.PRONTO);
        document.setModeloEmbedding(provider.model());
        document.setTentativasEmbedding(0);
        document.setErroEmbedding(null);
        document.setEmbeddingAtualizadoEm(now());
        documents.save(document);
        return true;
    }

    private void fail(Claim claim, RuntimeException exception) {
        boolean rateLimited = exception instanceof EmbeddingProviderException providerError
                && providerError.getStatusCode() == 429;
        transaction.executeWithoutResult(status -> {
            DocumentoSemantico document = documents.findByIdForUpdate(claim.documentId()).orElse(null);
            if (!stillReserved(document, claim)) return;
            document.setStatusEmbedding(DocumentoSemantico.StatusEmbedding.ERRO);
            document.setModeloEmbedding(provider.model());
            if (!rateLimited) document.setTentativasEmbedding(document.getTentativasEmbedding() + 1);
            document.setErroEmbedding(message(exception));
            document.setEmbeddingAtualizadoEm(now());
            documents.save(document);
        });
    }

    /** Se a preparacao recriou os trechos enquanto o provedor respondia, o resultado ja nao vale. */
    private boolean stillReserved(DocumentoSemantico document, Claim claim) {
        return document != null
                && document.getStatus() == DocumentoSemantico.Status.PRONTO
                && document.getStatusEmbedding() == DocumentoSemantico.StatusEmbedding.PROCESSANDO
                && Objects.equals(document.getHashConteudo(), claim.contentHash());
    }

    private String message(RuntimeException exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private record Claim(Long documentId, String contentHash, List<TrechoSemantico> chunks) {}

    public record BatchResult(int claimed, int embedded, int failed) {}
}
