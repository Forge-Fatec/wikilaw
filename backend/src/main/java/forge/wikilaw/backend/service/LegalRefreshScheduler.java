package forge.wikilaw.backend.service;

import forge.wikilaw.backend.config.LegalRefreshProperties;
import forge.wikilaw.backend.integration.datajud.DataJudProperties;
import forge.wikilaw.backend.integration.datajud.DataJudTribunal;
import forge.wikilaw.backend.integration.documents.DocumentSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class LegalRefreshScheduler {
    private static final Logger log = LoggerFactory.getLogger(LegalRefreshScheduler.class);
    private final LegalRefreshProperties properties;
    private final DataJudProperties datajud;
    private final ImportJobService jobs;

    public LegalRefreshScheduler(LegalRefreshProperties properties, DataJudProperties datajud, ImportJobService jobs) {
        this.properties = properties;
        this.datajud = datajud;
        this.jobs = jobs;
    }

    @Scheduled(initialDelayString = "${wikilaw.refresh.initial-delay:60000}",
            fixedDelayString = "${wikilaw.refresh.poll-delay:60000}")
    public void refresh() {
        if (!properties.isEnabled()) return;
        for (var source : DocumentSource.values()) {
            safely(source.name(), () -> jobs.refreshDocumentsIfDue(source, properties.getInterval(), properties.getPageSize()));
        }
        if (datajud.getApiKey() == null || datajud.getApiKey().isBlank()) return;
        for (var tribunal : DataJudTribunal.values()) {
            safely("DATAJUD:" + tribunal, () -> jobs.refreshDataJudIfDue(tribunal, properties.getInterval(), properties.getPageSize()));
        }
    }

    private void safely(String scope, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            log.warn("Não foi possível preparar atualização periódica {}: {}", scope, exception.getMessage());
        }
    }
}
