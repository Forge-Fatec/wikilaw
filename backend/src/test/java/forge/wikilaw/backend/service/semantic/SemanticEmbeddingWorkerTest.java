package forge.wikilaw.backend.service.semantic;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import forge.wikilaw.backend.integration.embedding.EmbeddingProperties;
import org.junit.jupiter.api.Test;

class SemanticEmbeddingWorkerTest {
    private final SemanticEmbeddingService service = mock(SemanticEmbeddingService.class);

    @Test
    void refusesToStartWithoutProviderConfiguration() {
        EmbeddingProperties properties = configured();
        properties.setModel("");

        assertThatThrownBy(() -> new SemanticEmbeddingWorker(service, properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WIKILAW_EMBEDDING_MODEL");
    }

    @Test
    void refusesDimensionsThatDoNotFitTheVectorColumn() {
        EmbeddingProperties properties = configured();
        properties.setDimensions(1536);

        assertThatThrownBy(() -> new SemanticEmbeddingWorker(service, properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1024");
    }

    @Test
    void keepsSchedulingAfterAFailedBatch() {
        when(service.processBatch()).thenThrow(new IllegalStateException("banco indisponivel"));
        var worker = new SemanticEmbeddingWorker(service, configured());

        assertThatCode(worker::process).doesNotThrowAnyException();
        verify(service).processBatch();
    }

    private EmbeddingProperties configured() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("https://api.voyageai.com");
        properties.setModel("voyage-4");
        properties.setDimensions(1024);
        return properties;
    }
}
