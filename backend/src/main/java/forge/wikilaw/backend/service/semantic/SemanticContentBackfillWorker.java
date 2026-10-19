package forge.wikilaw.backend.service.semantic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "wikilaw.semantic.backfill.enabled", havingValue = "true", matchIfMissing = true)
public class SemanticContentBackfillWorker {
    private static final Logger log = LoggerFactory.getLogger(SemanticContentBackfillWorker.class);
    private final SemanticContentBackfillService backfill;
    private final int batchSize;

    public SemanticContentBackfillWorker(SemanticContentBackfillService backfill,
            @Value("${wikilaw.semantic.backfill.batch-size:100}") int batchSize) {
        this.backfill = backfill;
        this.batchSize = batchSize;
    }

    @Scheduled(initialDelayString = "${wikilaw.semantic.backfill.initial-delay:5000}",
            fixedDelayString = "${wikilaw.semantic.backfill.poll-delay:2000}")
    public void process() {
        try {
            int processed = backfill.processBatch(batchSize);
            if (processed > 0) log.info("Conteudo semantico preparado: {} documentos", processed);
        } catch (RuntimeException exception) {
            log.error("Falha no lote de preparacao semantica", exception);
        }
    }
}

