package forge.wikilaw.backend.integration.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import forge.wikilaw.backend.exception.EmbeddingProviderException;
import forge.wikilaw.backend.integration.ai.GeminiProperties;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class GeminiEmbeddingClientTest {

    private static final String PATH = "/v1beta/models/gemini-embedding-001:batchEmbedContents";

    private final ObjectMapper json = JsonMapper.builder().build();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsDocumentsAsRetrievalDocumentsAndReturnsNormalizedVectorsInOrder() throws Exception {
        AtomicReference<JsonNode> request = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        start(exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            request.set(json.readTree(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, "{\"embeddings\":[{\"values\":[3.0,4.0]},{\"values\":[0.0,2.0]}]}");
        });

        List<float[]> vectors = client(properties()).embedDocuments(List.of("dano moral", "negativacao"));

        assertThat(apiKey.get()).isEqualTo("chave-gemini");
        JsonNode first = request.get().path("requests").path(0);
        assertThat(first.path("model").asString()).isEqualTo("models/gemini-embedding-001");
        assertThat(first.path("content").path("parts").path(0).path("text").asString()).isEqualTo("dano moral");
        assertThat(first.path("taskType").asString()).isEqualTo("RETRIEVAL_DOCUMENT");
        assertThat(first.path("outputDimensionality").asInt()).isEqualTo(2);
        assertThat(vectors.get(0)).containsExactly(new float[] {0.6f, 0.8f}, within(1e-6f));
        assertThat(vectors.get(1)).containsExactly(new float[] {0.0f, 1.0f}, within(1e-6f));
    }

    @Test
    void sendsTheSearchAsRetrievalQueryAndCutsTextsAboveTheModelLimit() throws Exception {
        AtomicReference<JsonNode> request = new AtomicReference<>();
        start(exchange -> {
            request.set(json.readTree(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, "{\"embeddings\":[{\"values\":[1.0,0.0]}]}");
        });

        client(properties()).embedQuery("x".repeat(GeminiEmbeddingClient.MAX_CHARACTERS + 500));

        JsonNode item = request.get().path("requests").path(0);
        assertThat(item.path("taskType").asString()).isEqualTo("RETRIEVAL_QUERY");
        assertThat(item.path("content").path("parts").path(0).path("text").asString())
                .hasSize(GeminiEmbeddingClient.MAX_CHARACTERS);
    }

    @Test
    void splitsBatchesAtTheGeminiLimitOfOneHundredTexts() throws Exception {
        List<Integer> sizes = new CopyOnWriteArrayList<>();
        start(exchange -> {
            int size = json.readTree(exchange.getRequestBody().readAllBytes()).path("requests").size();
            sizes.add(size);
            respond(exchange, 200, "{\"embeddings\":[" + String.join(",",
                    IntStream.range(0, size).mapToObj(i -> "{\"values\":[1.0,1.0]}").toList()) + "]}");
        });
        EmbeddingProperties properties = properties();
        properties.setBatchSize(128);

        List<float[]> vectors = client(properties).embedDocuments(
                IntStream.range(0, 150).mapToObj(i -> "trecho " + i).toList());

        assertThat(sizes).containsExactly(100, 50);
        assertThat(vectors).hasSize(150);
    }

    @Test
    void retriesRateLimitAndReportsItsStatusAfterTheLastAttempt() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        start(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 429, "{}");
        });

        assertThatThrownBy(() -> client(properties()).embedQuery("caso"))
                .isInstanceOf(EmbeddingProviderException.class)
                .extracting(error -> ((EmbeddingProviderException) error).getStatusCode()).isEqualTo(429);
        assertThat(calls).hasValue(2);
    }

    @Test
    void rejectsVectorsWithUnexpectedDimensionsOrCount() throws Exception {
        AtomicReference<String> answer = new AtomicReference<>();
        start(exchange -> respond(exchange, 200, answer.get()));
        GeminiEmbeddingClient client = client(properties());

        answer.set("{\"embeddings\":[{\"values\":[1.0,0.0,0.0]}]}");
        assertThatThrownBy(() -> client.embedQuery("caso")).hasMessageContaining("dimensoes");

        answer.set("{\"embeddings\":[]}");
        assertThatThrownBy(() -> client.embedQuery("caso")).hasMessageContaining("0 vetores");
    }

    @Test
    void usesTheDatabaseDimensionWhenNoneIsConfiguredAndRequiresTheGeminiKey() {
        EmbeddingProperties properties = properties();
        properties.setDimensions(0);
        assertThat(client(properties).dimensions()).isEqualTo(1024);

        GeminiProperties withoutKey = gemini();
        withoutKey.setApiKey("");
        assertThatThrownBy(() -> new GeminiEmbeddingClient(properties(), withoutKey, HttpClient.newHttpClient(), json)
                .embedQuery("caso")).hasMessageContaining("WIKILAW_GEMINI_API_KEY");
    }

    private GeminiEmbeddingClient client(EmbeddingProperties properties) {
        return new GeminiEmbeddingClient(properties, gemini(), HttpClient.newHttpClient(), json);
    }

    private EmbeddingProperties properties() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setEnabled(true);
        properties.setProvider(EmbeddingProperties.PROVIDER_GEMINI);
        properties.setModel("gemini-embedding-001");
        properties.setDimensions(2);
        properties.setMaxRetries(1);
        properties.setRetryDelay(Duration.ofMillis(1));
        return properties;
    }

    private GeminiProperties gemini() {
        GeminiProperties gemini = new GeminiProperties();
        gemini.setApiKey("chave-gemini");
        gemini.setBaseUrl(server == null ? "http://127.0.0.1:1" : "http://127.0.0.1:" + server.getAddress().getPort());
        return gemini;
    }

    private void start(Handler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(PATH, exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
