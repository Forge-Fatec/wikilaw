package forge.wikilaw.backend.integration.ai;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** API do Google Gemini ({@code models/{modelo}:generateContent}) usada na analise juridica assistida por IA. */
@Validated
@ConfigurationProperties(prefix = "wikilaw.ai.gemini")
@Getter
@Setter
public class GeminiProperties {

    private boolean enabled;

    @NotNull
    private String baseUrl = "https://generativelanguage.googleapis.com";

    /** Chave criada no Google AI Studio (https://aistudio.google.com/apikey). */
    @NotNull
    private String apiKey = "";

    @NotNull
    private String model = "gemini-2.5-flash";

    /** Valores baixos deixam a analise mais fiel aos documentos e menos criativa. */
    @DecimalMin("0.0")
    @DecimalMax("2.0")
    private double temperature = 0.2;

    /** Inclui os tokens de raciocinio dos modelos 2.5; valores baixos podem cortar a resposta. */
    @Min(256)
    @Max(65_536)
    private int maxOutputTokens = 8192;

    /**
     * Teto de tokens de raciocinio dos modelos 2.5, descontado de {@link #maxOutputTokens}.
     * Com 2048 sobram ao menos 6144 tokens para a analise. Use -1 para o modelo decidir sozinho.
     */
    @Min(-1)
    @Max(32_768)
    private int thinkingBudget = 2048;

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(5);

    @NotNull
    private Duration readTimeout = Duration.ofSeconds(90);

    @Min(0)
    @Max(5)
    private int maxRetries = 1;

    @NotNull
    private Duration retryDelay = Duration.ofSeconds(2);
}
