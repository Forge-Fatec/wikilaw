package forge.wikilaw.backend.service;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import forge.wikilaw.backend.config.LegalRefreshProperties;
import forge.wikilaw.backend.integration.datajud.DataJudProperties;
import forge.wikilaw.backend.integration.datajud.DataJudTribunal;
import forge.wikilaw.backend.integration.documents.DocumentSource;
import org.junit.jupiter.api.Test;

class LegalRefreshSchedulerTest {
    private final LegalRefreshProperties properties = new LegalRefreshProperties();
    private final DataJudProperties datajud = new DataJudProperties();
    private final ImportJobService jobs = mock(ImportJobService.class);
    private final LegalRefreshScheduler scheduler = new LegalRefreshScheduler(properties, datajud, jobs);

    @Test
    void disabledRefreshDoesNotEnqueueAnything() {
        properties.setEnabled(false);
        scheduler.refresh();
        verifyNoInteractions(jobs);
    }

    @Test
    void missingApiKeySkipsDataJudButRefreshesEveryDocumentSource() {
        scheduler.refresh();
        for (var source : DocumentSource.values()) {
            verify(jobs).refreshDocumentsIfDue(source, properties.getInterval(), properties.getPageSize());
        }
        verifyNoMoreInteractions(jobs);
    }

    @Test
    void failingSourceDoesNotStopOtherSourcesOrTribunals() {
        datajud.setApiKey("test-key");
        doThrow(new IllegalStateException("Fonte indisponível"))
                .when(jobs).refreshDocumentsIfDue(eq(DocumentSource.STJ), any(), anyInt());
        doThrow(new IllegalStateException("Tribunal indisponível"))
                .when(jobs).refreshDataJudIfDue(eq(DataJudTribunal.TJSP), any(), anyInt());
        scheduler.refresh();
        for (var source : DocumentSource.values()) {
            verify(jobs).refreshDocumentsIfDue(source, properties.getInterval(), properties.getPageSize());
        }
        for (var tribunal : DataJudTribunal.values()) {
            verify(jobs).refreshDataJudIfDue(tribunal, properties.getInterval(), properties.getPageSize());
        }
    }
}
