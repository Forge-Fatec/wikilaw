package forge.wikilaw.backend.integration.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class EmbeddingPropertiesTest {

    @Test
    void staysDisabledWithSafeDefaultsWhenNothingIsConfigured() {
        EmbeddingProperties properties = bind(Map.of());

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getBaseUrl()).isEmpty();
        assertThat(properties.getModel()).isEmpty();
        assertThat(properties.getBatchSize()).isEqualTo(32);
        assertThat(properties.getDocumentPrefix()).isEmpty();
        assertThat(properties.getQueryPrefix()).isEmpty();
        assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.getMaxRetries()).isEqualTo(2);
    }

    @Test
    void bindsProviderConfiguration() {
        EmbeddingProperties properties = bind(Map.of(
                "wikilaw.semantic.embedding.enabled", "true",
                "wikilaw.semantic.embedding.base-url", "http://localhost:11434",
                "wikilaw.semantic.embedding.model", "modelo-teste",
                "wikilaw.semantic.embedding.dimensions", "1024",
                "wikilaw.semantic.embedding.batch-size", "16",
                "wikilaw.semantic.embedding.query-prefix", "query: ",
                "wikilaw.semantic.embedding.retry-delay", "250ms"));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getBaseUrl()).isEqualTo("http://localhost:11434");
        assertThat(properties.getModel()).isEqualTo("modelo-teste");
        assertThat(properties.getDimensions()).isEqualTo(1024);
        assertThat(properties.getBatchSize()).isEqualTo(16);
        assertThat(properties.getQueryPrefix()).isEqualTo("query: ");
        assertThat(properties.getRetryDelay()).isEqualTo(Duration.ofMillis(250));
    }

    @Test
    void bindsWorkerSettingsWithConservativeDefaults() {
        EmbeddingProperties defaults = bind(Map.of());
        assertThat(defaults.getWorker().getDocumentsPerBatch()).isEqualTo(10);
        assertThat(defaults.getWorker().getMaxAttempts()).isEqualTo(5);
        assertThat(defaults.getWorker().getRetryBackoff()).isEqualTo(Duration.ofMinutes(5));
        assertThat(defaults.getWorker().getProcessingTimeout()).isEqualTo(Duration.ofMinutes(15));

        EmbeddingProperties configured = bind(Map.of(
                "wikilaw.semantic.embedding.worker.documents-per-batch", "25",
                "wikilaw.semantic.embedding.worker.max-attempts", "3",
                "wikilaw.semantic.embedding.worker.retry-backoff", "30s",
                "wikilaw.semantic.embedding.worker.processing-timeout", "2m"));
        assertThat(configured.getWorker().getDocumentsPerBatch()).isEqualTo(25);
        assertThat(configured.getWorker().getMaxAttempts()).isEqualTo(3);
        assertThat(configured.getWorker().getRetryBackoff()).isEqualTo(Duration.ofSeconds(30));
        assertThat(configured.getWorker().getProcessingTimeout()).isEqualTo(Duration.ofMinutes(2));
    }

    private EmbeddingProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("wikilaw.semantic.embedding", EmbeddingProperties.class);
    }
}
