package forge.wikilaw.backend.integration.embedding;

import forge.wikilaw.backend.exception.EmbeddingProviderException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Cliente da rota {@code POST /v1/embeddings}, aceita por Voyage AI, Ollama, LM Studio e OpenAI.
 * Os vetores sao normalizados (norma L2 = 1) para que a similaridade de cosseno seja um produto escalar.
 */
@Component
public class OpenAiCompatibleEmbeddingClient implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleEmbeddingClient.class);
    private static final String INPUT_TYPE_DOCUMENT = "document";
    private static final String INPUT_TYPE_QUERY = "query";

    private final EmbeddingProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper json;

    @Autowired
    public OpenAiCompatibleEmbeddingClient(EmbeddingProperties properties, ObjectMapper json) {
        this(properties, HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build(), json);
    }

    OpenAiCompatibleEmbeddingClient(EmbeddingProperties properties, HttpClient httpClient, ObjectMapper json) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.json = json;
    }

    @Override
    public String model() {
        return properties.getModel();
    }

    @Override
    public int dimensions() {
        return properties.getDimensions();
    }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        if (texts.isEmpty()) return List.of();
        var vectors = new ArrayList<float[]>(texts.size());
        for (int start = 0; start < texts.size(); start += properties.getBatchSize()) {
            List<String> batch = texts.subList(start, Math.min(texts.size(), start + properties.getBatchSize()));
            vectors.addAll(embed(batch.stream().map(text -> properties.getDocumentPrefix() + text).toList(),
                    INPUT_TYPE_DOCUMENT));
        }
        return List.copyOf(vectors);
    }

    @Override
    public float[] embedQuery(String text) {
        return embed(List.of(properties.getQueryPrefix() + text), INPUT_TYPE_QUERY).getFirst();
    }

    private List<float[]> embed(List<String> inputs, String inputType) {
        ensureConfigured();
        for (String input : inputs) {
            if (input == null || input.isBlank())
                throw new EmbeddingProviderException("Texto vazio nao pode gerar embedding", 0);
        }
        String body = requestBody(inputs, inputType);
        URI endpoint = URI.create(stripTrailingSlash(properties.getBaseUrl()) + "/v1/embeddings");
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(properties.getReadTimeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (!properties.getApiKey().isBlank())
            request.header("Authorization", "Bearer " + properties.getApiKey());
        return parse(send(request.build()), inputs.size());
    }

    private String send(HttpRequest request) {
        int maxAttempts = Math.max(1, properties.getMaxRetries() + 1);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 200 && status < 300) return response.body();
                if (!isRetryable(status) || attempt == maxAttempts)
                    throw new EmbeddingProviderException("Provedor de embeddings respondeu com HTTP " + status, status);
                log.warn("Falha transitoria do provedor de embeddings status={} tentativa={}/{}",
                        status, attempt, maxAttempts);
            } catch (IOException exception) {
                if (attempt == maxAttempts)
                    throw new EmbeddingProviderException("Falha de comunicacao com o provedor de embeddings", exception);
                log.warn("Falha de I/O no provedor de embeddings tentativa={}/{}: {}",
                        attempt, maxAttempts, exception.getMessage());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new EmbeddingProviderException("Chamada ao provedor de embeddings interrompida", exception);
            }
            waitBeforeRetry(attempt);
        }
        throw new EmbeddingProviderException("Chamada ao provedor de embeddings falhou", 0);
    }

    private String requestBody(List<String> inputs, String inputType) {
        var root = json.createObjectNode();
        root.put("model", properties.getModel());
        var input = root.putArray("input");
        inputs.forEach(input::add);
        if (properties.isSendInputType()) root.put("input_type", inputType);
        return json.writeValueAsString(root);
    }

    private List<float[]> parse(String body, int expected) {
        JsonNode data;
        try {
            data = json.readTree(body).path("data");
        } catch (JacksonException exception) {
            throw new EmbeddingProviderException("Resposta invalida do provedor de embeddings", exception);
        }
        if (!data.isArray() || data.size() != expected)
            throw new EmbeddingProviderException(
                    "Provedor de embeddings retornou " + data.size() + " vetores para " + expected + " textos", 0);

        var vectors = new float[expected][];
        for (JsonNode item : data) {
            int index = item.path("index").asInt(-1);
            if (index < 0 || index >= expected || vectors[index] != null)
                throw new EmbeddingProviderException("Indice de embedding invalido: " + index, 0);
            vectors[index] = normalize(vector(item.path("embedding")));
        }
        return List.of(vectors);
    }

    private float[] vector(JsonNode values) {
        if (!values.isArray() || values.isEmpty())
            throw new EmbeddingProviderException("Provedor de embeddings retornou vetor vazio", 0);
        int dimensions = properties.getDimensions();
        if (dimensions > 0 && values.size() != dimensions)
            throw new EmbeddingProviderException("Vetor com " + values.size()
                    + " dimensoes, mas o esperado era " + dimensions, 0);
        var vector = new float[values.size()];
        for (int i = 0; i < vector.length; i++) {
            JsonNode value = values.get(i);
            if (!value.isNumber())
                throw new EmbeddingProviderException("Provedor de embeddings retornou valor nao numerico", 0);
            vector[i] = (float) value.asDouble();
        }
        return vector;
    }

    static float[] normalize(float[] vector) {
        double sum = 0;
        for (float value : vector) sum += (double) value * value;
        double norm = Math.sqrt(sum);
        if (norm == 0 || Double.isNaN(norm) || Double.isInfinite(norm))
            throw new EmbeddingProviderException("Provedor de embeddings retornou vetor invalido", 0);
        var normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) normalized[i] = (float) (vector[i] / norm);
        return normalized;
    }

    private void ensureConfigured() {
        if (!properties.isEnabled())
            throw new EmbeddingProviderException("Geracao de embeddings desabilitada", 0);
        if (properties.getBaseUrl().isBlank() || properties.getModel().isBlank())
            throw new EmbeddingProviderException("Configure WIKILAW_EMBEDDING_BASE_URL e WIKILAW_EMBEDDING_MODEL", 0);
    }

    private boolean isRetryable(int status) {
        return status == 429 || status >= 500;
    }

    private void waitBeforeRetry(int attempt) {
        long delay = properties.getRetryDelay().toMillis() * attempt;
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EmbeddingProviderException("Espera de retry do provedor de embeddings interrompida", exception);
        }
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
