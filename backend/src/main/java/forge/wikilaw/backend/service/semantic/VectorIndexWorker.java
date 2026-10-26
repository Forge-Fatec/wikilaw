package forge.wikilaw.backend.service.semantic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="wikilaw.vector.enabled", havingValue="true")
public class VectorIndexWorker {
    private static final Logger log=LoggerFactory.getLogger(VectorIndexWorker.class);
    private final VectorIndexService index;
    private long retryAfter;
    private boolean prepared;
    public VectorIndexWorker(VectorIndexService index) { this.index=index; }

    @Scheduled(scheduler="vectorTaskScheduler", initialDelayString="${wikilaw.vector.initial-delay:10000}", fixedDelayString="${wikilaw.vector.poll-delay:2000}")
    public void process() {
        if (System.currentTimeMillis()<retryAfter) return;
        try {
            if (!prepared) { index.prepareModel(); prepared=true; }
            index.indexBatch();
        }
        catch (RuntimeException exception) {
            retryAfter=System.currentTimeMillis()+30000;
            log.warn("Indexacao vetorial indisponivel; lote preservado para nova tentativa em 30 segundos");
        }
    }
}
