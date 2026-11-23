package forge.wikilaw.backend.integration.embedding;

import forge.wikilaw.backend.exception.EmbeddingProviderException;
import forge.wikilaw.backend.integration.ai.GeminiProperties;
import forge.wikilaw.backend.repository.EmbeddingTrechoRepository;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Embeddings do Google Gemini pela rota {@code models/{modelo}:batchEmbedContents}.
 * Usa a mesma chave da analise com IA ({@code WIKILAW_GEMINI_API_KEY}). Os trechos vao como
 * {@code RETRIEVAL_DOCUMENT} e a consulta como {@code RETRIEVAL_QUERY}, no mesmo espaco vetorial.
 * Abaixo de 3072 dimensoes o Gemini nao normaliza o vetor, entao ele e normalizado aqui.
 */
@Component
@ConditionalOnProperty(prefix = "wikilaw.semantic.embedding", name = "provider",
        havingValue = EmbeddingProperties.PROVIDER_GEMINI)
public class GeminiEmbeddingClient implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiEmbeddingClient.class);

    /** Limite de pedidos por chamada de batchEmbedContents. */
    static final int MAX_BATCH = 100;
    /** O modelo aceita 2.048 tokens por texto; ~7 mil caracteres em portugues ficam abaixo disso. */
    static final int MAX_CHARACTERS = 7_000;
    private static final String TASK_DOCUMENT = "RETRIEVAL_DOCUMENT";
    private static final String TASK_QUERY = "RETRIEVAL_QUERY";

    private final EmbeddingProperties properties;
    private final GeminiProperties gemini;
    private final HttpClient httpClient;
    private final ObjectMapper json;

    @Autowired
    public GeminiEmbeddingClient(EmbeddingProperties properties, GeminiProperties gemini, ObjectMapper json) {
        this(properties, gemini, HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build(), json);
    }

    GeminiEmbeddingClient(EmbeddingProperties properties, GeminiProperties gemini, HttpClient httpClient,
            ObjectMapper json) {
        this.properties = properties;
        this.gemini = gemini;
        this.httpClient = httpClient;
        this.json = json;
    }

    @Override
    public String model() {
        return properties.getModel();
    }

    /** Sem valor configurado, usa a dimensao da coluna {@code embedding_trecho.vetor}. */
    @Override
    public int dimensions() {
        return properties.getDimensions() > 0 ? properties.getDimensions() : EmbeddingTrechoRepository.DIMENSIONS;
    }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        if (texts.isEmpty()) return List.of();
        int batchSize = Math.min(properties.getBatchSize(), MAX_BATCH);
        var vectors = new ArrayList<float[]>(texts.size());
        for (int start = 0; start < texts.size(); start += batchSize) {
            List<String> batch = texts.subList(start, Math.min(texts.size(), start + batchSize));
            vectors.addAll(embed(batch.stream().map(text -> properties.getDocumentPrefix() + text).toList(),
                    TASK_DOCUMENT));
        }
        return List.copyOf(vectors);
    }

    @Override
    public float[] embedQuery(String text) {
        return embed(List.of(properties.getQueryPrefix() + text), TASK_QUERY).getFirst();
    }

    private List<float[]> embed(List<String> inputs, String taskType) {
        ensureConfigured();
        for (String input : inputs) {
            if (input == null || input.isBlank())
                throw new EmbeddingProviderException("Texto vazio nao pode gerar embedding", 0);
        }
        URI endpoint = URI.create(stripTrailingSlash(gemini.getBaseUrl()) + "/v1beta/models/"
                + URLEncoder.encode(properties.getModel(), StandardCharsets.UTF_8) + ":batchEmbedContents");
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(properties.getReadTimeout())
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", gemini.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(inputs, taskType)))
                .build();
        return parse(send(request), inputs.size());
    }

    private String requestBody(List<String> inputs, String taskType) {
        var root = json.createObjectNode();
        var requests = root.putArray("requests");
        for (String input : inputs) {
            var item = requests.addObject();
            item.put("model", "models/" + properties.getModel());
            String text = input.length() > MAX_CHARACTERS ? input.substring(0, MAX_CHARACTERS) : input;
            item.putObject("content").putArray("parts").addObject().put("text", text);
            item.put("taskType", taskType);
            item.put("outputDimensionality", dimensions());
        }
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
                    throw new EmbeddingProviderException("Gemini (embeddings) respondeu com HTTP " + status, status);
                log.warn("Falha transitoria do Gemini (embeddings) status={} tentativa={}/{}",
                        status, attempt, maxAttempts);
            } catch (IOException exception) {
                if (attempt == maxAttempts)
                    throw new EmbeddingProviderException("Falha de comunicacao com o Gemini (embeddings)", exception);
                log.warn("Falha de I/O no Gemini (embeddings) tentativa={}/{}: {}",
                        attempt, maxAttempts, exception.getMessage());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new EmbeddingProviderException("Chamada ao Gemini (embeddings) interrompida", exception);
            }
            waitBeforeRetry(attempt);
        }
        throw new EmbeddingProviderException("Chamada ao Gemini (embeddings) falhou", 0);
    }

    private List<float[]> parse(String body, int expected) {
        JsonNode embeddings;
        try {
            embeddings = json.readTree(body).path("embeddings");
        } catch (JacksonException exception) {
            throw new EmbeddingProviderException("Resposta invalida do Gemini (embeddings)", exception);
        }
        if (!embeddings.isArray() || embeddings.size() != expected)
            throw new EmbeddingProviderException(
                    "Gemini retornou " + embeddings.size() + " vetores para " + expected + " textos", 0);
        var vectors = new ArrayList<float[]>(expected);
        for (JsonNode item : embeddings) vectors.add(OpenAiCompatibleEmbeddingClient.normalize(vector(item.path("values"))));
        return List.copyOf(vectors);
    }

    private float[] vector(JsonNode values) {
        if (!values.isArray() || values.isEmpty())
            throw new EmbeddingProviderException("Gemini retornou vetor vazio", 0);
        if (values.size() != dimensions())
            throw new EmbeddingProviderException("Vetor com " + values.size()
                    + " dimensoes, mas o esperado era " + dimensions(), 0);
        var vector = new float[values.size()];
        for (int i = 0; i < vector.length; i++) {
            JsonNode value = values.get(i);
            if (!value.isNumber())
                throw new EmbeddingProviderException("Gemini retornou valor nao numerico", 0);
            vector[i] = (float) value.asDouble();
        }
        return vector;
    }

    private void ensureConfigured() {
        if (!properties.isEnabled())
            throw new EmbeddingProviderException("Geracao de embeddings desabilitada", 0);
        if (properties.getModel().isBlank())
            throw new EmbeddingProviderException("Configure WIKILAW_EMBEDDING_MODEL", 0);
        if (gemini.getApiKey().isBlank() || gemini.getBaseUrl().isBlank())
            throw new EmbeddingProviderException("Configure WIKILAW_GEMINI_API_KEY para gerar embeddings com o Gemini", 0);
    }

    private boolean isRetryable(int status) {
        return status == 429 || status >= 500;
    }

    private void waitBeforeRetry(int attempt) {
        try {
            Thread.sleep(properties.getRetryDelay().toMillis() * attempt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EmbeddingProviderException("Espera de retry do Gemini (embeddings) interrompida", exception);
        }
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
