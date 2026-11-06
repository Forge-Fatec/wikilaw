package forge.wikilaw.backend.integration.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.integration.ai.GeminiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class EmbeddingProviderSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class, GeminiEmbeddingClient.class, OpenAiCompatibleEmbeddingClient.class);

    @Test
    void usesGeminiWhenConfigured() {
        runner.withPropertyValues("wikilaw.semantic.embedding.provider=gemini").run(context ->
                assertThat(context).hasSingleBean(EmbeddingProvider.class).hasSingleBean(GeminiEmbeddingClient.class));
    }

    @Test
    void keepsTheOpenAiCompatibleClientForVoyageAndOllama() {
        runner.withPropertyValues("wikilaw.semantic.embedding.provider=openai-compatible").run(context ->
                assertThat(context).hasSingleBean(EmbeddingProvider.class)
                        .hasSingleBean(OpenAiCompatibleEmbeddingClient.class));
    }

    @Configuration
    @EnableConfigurationProperties({EmbeddingProperties.class, GeminiProperties.class})
    static class Config {
        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }
    }
}
