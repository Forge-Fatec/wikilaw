package forge.wikilaw.backend.integration.embedding;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Provedor de embeddings compativel com a API {@code /v1/embeddings} (Ollama, LM Studio, OpenAI). */
@Validated
@ConfigurationProperties(prefix = "wikilaw.semantic.embedding")
@Getter
@Setter
public class EmbeddingProperties {

    private boolean enabled;

    @NotNull
    private String baseUrl = "";

    @NotNull
    private String apiKey = "";

    @NotNull
    private String model = "";

    @Min(0)
    private int dimensions;

    @Min(1)
    @Max(256)
    private int batchSize = 32;

    /** Prefixo exigido por alguns modelos para textos indexados (ex.: "passage: "). */
    @NotNull
    private String documentPrefix = "";

    /** Prefixo exigido por alguns modelos para a consulta (ex.: "query: "). */
    @NotNull
    private String queryPrefix = "";

    /** Envia {@code input_type} ("document"/"query"), aceito pela Voyage AI e recusado pela OpenAI. */
    private boolean sendInputType;

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(5);

    @NotNull
    private Duration readTimeout = Duration.ofSeconds(60);

    @Min(0)
    @Max(10)
    private int maxRetries = 2;

    @NotNull
    private Duration retryDelay = Duration.ofSeconds(1);

    @Valid
    @NotNull
    private Worker worker = new Worker();

    /** Rotina em segundo plano que gera os vetores dos documentos preparados. */
    @Getter
    @Setter
    public static class Worker {

        @Min(1)
        @Max(100)
        private int documentsPerBatch = 10;

        /** Falhas por documento antes de desistir; limite de requisicoes (HTTP 429) nao conta. */
        @Min(1)
        @Max(50)
        private int maxAttempts = 5;

        /** Espera minima antes de tentar de novo um documento com erro. */
        @NotNull
        private Duration retryBackoff = Duration.ofMinutes(5);

        /** Documento em PROCESSANDO ha mais tempo que isso e considerado abandonado e volta para a fila. */
        @NotNull
        private Duration processingTimeout = Duration.ofMinutes(15);
    }
}
