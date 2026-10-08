package forge.wikilaw.backend.service;

import forge.wikilaw.backend.config.JurisprudenciaBootstrapProperties;
import forge.wikilaw.backend.integration.datajud.*;
import forge.wikilaw.backend.integration.datajud.dto.DataJudImportRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DataJudBootstrapService {
    private static final Logger log = LoggerFactory.getLogger(DataJudBootstrapService.class);
    private final JurisprudenciaBootstrapProperties bootstrap;
    private final DataJudProperties datajud;
    private final ImportJobService jobs;

    public DataJudBootstrapService(JurisprudenciaBootstrapProperties bootstrap, DataJudProperties datajud,
            ImportJobService jobs) {
        this.bootstrap=bootstrap; this.datajud=datajud; this.jobs=jobs;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!bootstrap.isEnabled() || !bootstrap.isCompleto()) return;
        if (datajud.getApiKey()==null || datajud.getApiKey().isBlank()) {
            log.warn("Carga completa DataJud não iniciada: configure DATAJUD_API_KEY");
            return;
        }
        reconcile();
    }

    @Scheduled(initialDelay = 30000, fixedDelay = 30000)
    public void reconcile() {
        if (!bootstrap.isEnabled() || !bootstrap.isCompleto() || datajud.getApiKey()==null || datajud.getApiKey().isBlank()) return;
        for (var tribunal : DataJudTribunal.values()) {
            try {
                jobs.ensureDataJudComplete(new DataJudImportRequest(tribunal,null,bootstrap.getTamanhoPagina(),1));
            } catch (RuntimeException exception) {
                log.warn("Não foi possível preparar carga completa DataJud {}: {}",tribunal,exception.getMessage());
            }
        }
    }
}
