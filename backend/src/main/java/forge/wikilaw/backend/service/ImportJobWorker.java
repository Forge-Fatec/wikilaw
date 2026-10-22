package forge.wikilaw.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "wikilaw.import.worker.enabled", havingValue = "true", matchIfMissing = true)
public class ImportJobWorker {
    private static final Logger log = LoggerFactory.getLogger(ImportJobWorker.class);
    private final ImportJobProcessor processor;

    public ImportJobWorker(ImportJobProcessor processor) { this.processor = processor; }

    @Scheduled(initialDelayString = "${wikilaw.import.worker.poll-delay:1000}",
            fixedDelayString = "${wikilaw.import.worker.poll-delay:1000}")
    public void poll() {
        try {
            processor.prepareNext().ifPresent(processor::processPage);
        } catch (RuntimeException exception) {
            // A failed checkpoint rolls back. The next poll can replay the page.
            log.error("Worker de importação falhou; checkpoint preservado", exception);
        }
    }
}
