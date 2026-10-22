package forge.wikilaw.backend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import forge.wikilaw.backend.config.JurisprudenciaBootstrapProperties;
import forge.wikilaw.backend.integration.datajud.DataJudProperties;
import forge.wikilaw.backend.integration.datajud.DataJudTribunal;
import forge.wikilaw.backend.integration.datajud.dto.DataJudImportRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DataJudBootstrapServiceTest {
    @Test
    void queuesAllIntegratedTribunalsWhenKeyIsConfigured() {
        var properties = new JurisprudenciaBootstrapProperties();
        var datajud = new DataJudProperties(); datajud.setApiKey("test-key");
        var jobs = mock(ImportJobService.class);
        var bootstrap = new DataJudBootstrapService(properties,datajud,jobs);
        bootstrap.start();
        var requests = ArgumentCaptor.forClass(DataJudImportRequest.class);
        verify(jobs,times(3)).ensureDataJudComplete(requests.capture());
        assertThat(requests.getAllValues()).extracting(DataJudImportRequest::tribunal)
                .containsExactly(DataJudTribunal.TJSP,DataJudTribunal.TJRJ,DataJudTribunal.TJMG);
        assertThat(requests.getAllValues()).allSatisfy(r -> assertThat(r.numeroProcesso()).isNull());
    }

    @Test
    void missingKeyOrDisabledBootstrapDoesNotQueueDataJud() {
        var properties = new JurisprudenciaBootstrapProperties();
        var datajud = new DataJudProperties();
        var jobs = mock(ImportJobService.class);
        var bootstrap = new DataJudBootstrapService(properties,datajud,jobs);
        bootstrap.start();
        properties.setEnabled(false); datajud.setApiKey("test-key");
        bootstrap.start(); bootstrap.reconcile();
        verify(jobs,never()).ensureDataJudComplete(any());
    }
}
