package forge.wikilaw.backend.service.semantic;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import forge.wikilaw.backend.integration.ai.GeminiProperties;
import forge.wikilaw.backend.integration.embedding.EmbeddingProperties;
import forge.wikilaw.backend.integration.embedding.EmbeddingProvider;
import org.junit.jupiter.api.Test;

class SemanticEmbeddingWorkerTest {
    private final SemanticEmbeddingService service = mock(SemanticEmbeddingService.class);
    private final EmbeddingProvider provider = mock(EmbeddingProvider.class);

    @Test
    void refusesToStartWithoutProviderConfiguration() {
        EmbeddingProperties properties = configured();
        properties.setModel("");

        assertThatThrownBy(() -> worker(properties, gemini("chave")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WIKILAW_EMBEDDING_MODEL");
    }

    @Test
    void requiresTheBaseUrlOnlyForOpenAiCompatibleProviders() {
        EmbeddingProperties openAi = configured();
        openAi.setBaseUrl("");
        assertThatThrownBy(() -> worker(openAi, gemini("chave")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WIKILAW_EMBEDDING_BASE_URL");

        EmbeddingProperties geminiEmbeddings = configured();
        geminiEmbeddings.setProvider(EmbeddingProperties.PROVIDER_GEMINI);
        geminiEmbeddings.setBaseUrl("");
        assertThatCode(() -> worker(geminiEmbeddings, gemini("chave"))).doesNotThrowAnyException();
    }

    @Test
    void refusesDimensionsThatDoNotFitTheVectorColumn() {
        when(provider.dimensions()).thenReturn(1536);

        assertThatThrownBy(() -> new SemanticEmbeddingWorker(service, configured(), provider, gemini("chave")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1024");
    }

    @Test
    void staysIdleWithoutAGeminiKeyInsteadOfFailingEveryDocument() {
        EmbeddingProperties properties = configured();
        properties.setProvider(EmbeddingProperties.PROVIDER_GEMINI);

        worker(properties, gemini("")).process();

        verify(service, never()).processBatch();
    }

    @Test
    void keepsSchedulingAfterAFailedBatch() {
        when(service.processBatch()).thenThrow(new IllegalStateException("banco indisponivel"));
        var worker = worker(configured(), gemini(""));

        assertThatCode(worker::process).doesNotThrowAnyException();
        verify(service).processBatch();
    }

    private SemanticEmbeddingWorker worker(EmbeddingProperties properties, GeminiProperties gemini) {
        when(provider.dimensions()).thenReturn(1024);
        return new SemanticEmbeddingWorker(service, properties, provider, gemini);
    }

    private GeminiProperties gemini(String apiKey) {
        GeminiProperties gemini = new GeminiProperties();
        gemini.setApiKey(apiKey);
        return gemini;
    }

    private EmbeddingProperties configured() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setEnabled(true);
        properties.setProvider(EmbeddingProperties.PROVIDER_OPENAI_COMPATIBLE);
        properties.setBaseUrl("https://api.voyageai.com");
        properties.setModel("voyage-4");
        properties.setDimensions(1024);
        return properties;
    }
}
