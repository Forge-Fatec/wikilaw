package forge.wikilaw.backend.integration.ai;

import forge.wikilaw.backend.exception.AiProviderException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Cliente da rota {@code POST /v1beta/models/{modelo}:generateContent} do Gemini com saida estruturada em JSON.
 * O texto enviado nunca e registrado em log.
 */
@Component
public class GeminiClient implements GenerativeAiProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final GeminiProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper json;

    @Autowired
    public GeminiClient(GeminiProperties properties, ObjectMapper json) {
        this(properties, HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build(), json);
    }

    GeminiClient(GeminiProperties properties, HttpClient httpClient, ObjectMapper json) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.json = json;
    }

    @Override
    public String model() {
        return properties.getModel();
    }

    @Override
    public JsonNode generateJson(String systemInstruction, String prompt, JsonNode responseSchema) {
        ensureConfigured();
        URI endpoint = URI.create(stripTrailingSlash(properties.getBaseUrl()) + "/v1beta/models/"
                + URLEncoder.encode(properties.getModel(), StandardCharsets.UTF_8) + ":generateContent");
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(properties.getReadTimeout())
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", properties.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(systemInstruction, prompt, responseSchema)))
                .build();
        return parse(send(request));
    }

    private String requestBody(String systemInstruction, String prompt, JsonNode responseSchema) {
        var root = json.createObjectNode();
        root.putObject("systemInstruction").putArray("parts").addObject().put("text", systemInstruction);
        var content = root.putArray("contents").addObject();
        content.put("role", "user");
        content.putArray("parts").addObject().put("text", prompt);
        var config = root.putObject("generationConfig");
        config.put("temperature", properties.getTemperature());
        config.put("maxOutputTokens", properties.getMaxOutputTokens());
        config.put("responseMimeType", "application/json");
        config.set("responseSchema", responseSchema);
        // Sem teto, o raciocinio pode consumir o limite de saida e cortar o JSON (finishReason MAX_TOKENS).
        if (properties.getThinkingBudget() >= 0)
            config.putObject("thinkingConfig").put("thinkingBudget", properties.getThinkingBudget());
        return json.writeValueAsString(root);
    }

    private String send(HttpRequest request) {
        int maxAttempts = Math.max(1, properties.getMaxRetries() + 1);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 200 && status < 300) return response.body();
                if (!isRetryable(status) || attempt == maxAttempts)
                    throw new AiProviderException("Gemini respondeu com HTTP " + status, status);
                log.warn("Falha transitoria do Gemini status={} tentativa={}/{}", status, attempt, maxAttempts);
            } catch (IOException exception) {
                if (attempt == maxAttempts)
                    throw new AiProviderException("Falha de comunicacao com o Gemini", exception);
                log.warn("Falha de I/O no Gemini tentativa={}/{}: {}", attempt, maxAttempts, exception.getMessage());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AiProviderException("Chamada ao Gemini interrompida", exception);
            }
            waitBeforeRetry(attempt);
        }
        throw new AiProviderException("Chamada ao Gemini falhou", 0);
    }

    private JsonNode parse(String body) {
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (JacksonException exception) {
            throw new AiProviderException("Resposta invalida do Gemini", exception);
        }
        String blockReason = root.path("promptFeedback").path("blockReason").asString("");
        if (!blockReason.isBlank())
            throw new AiProviderException("O Gemini recusou a solicitacao (" + blockReason + ")", 0);

        JsonNode candidate = root.path("candidates").path(0);
        if (candidate.isMissingNode())
            throw new AiProviderException("O Gemini nao retornou nenhuma resposta", 0);
        String finishReason = candidate.path("finishReason").asString("");
        if (!finishReason.isBlank() && !"STOP".equals(finishReason))
            throw new AiProviderException("O Gemini interrompeu a resposta (" + finishReason + ")", 0);

        var text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (part.path("thought").asBoolean(false)) continue;
            text.append(part.path("text").asString(""));
        }
        if (text.isEmpty())
            throw new AiProviderException("O Gemini retornou uma resposta vazia", 0);
        JsonNode result;
        try {
            result = json.readTree(text.toString());
        } catch (JacksonException exception) {
            throw new AiProviderException("O Gemini retornou um JSON invalido", exception);
        }
        if (!result.isObject())
            throw new AiProviderException("O Gemini retornou um JSON fora do formato esperado", 0);
        return result;
    }

    private void ensureConfigured() {
        if (properties.getThinkingBudget() >= properties.getMaxOutputTokens())
            throw new AiProviderException("WIKILAW_GEMINI_THINKING_BUDGET deve ser menor que WIKILAW_GEMINI_MAX_OUTPUT_TOKENS", 0);
        if (!properties.isEnabled())
            throw new AiProviderException("Analise por IA desabilitada (WIKILAW_GEMINI_ENABLED=false)", 0);
        if (properties.getApiKey().isBlank() || properties.getModel().isBlank() || properties.getBaseUrl().isBlank())
            throw new AiProviderException("Configure WIKILAW_GEMINI_API_KEY e WIKILAW_GEMINI_MODEL", 0);
    }

    private boolean isRetryable(int status) {
        return status == 429 || status >= 500;
    }

    private void waitBeforeRetry(int attempt) {
        try {
            Thread.sleep(properties.getRetryDelay().toMillis() * attempt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("Espera de retry do Gemini interrompida", exception);
        }
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
