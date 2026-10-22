package forge.wikilaw.backend.integration.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import forge.wikilaw.backend.exception.EmbeddingProviderException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class OpenAiCompatibleEmbeddingClientTest {

    private final ObjectMapper json = JsonMapper.builder().build();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsModelAndPrefixedInputsAndReturnsNormalizedVectorsInInputOrder() throws Exception {
        List<JsonNode> requests = new CopyOnWriteArrayList<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        start(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requests.add(json.readTree(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, """
                    {"data":[
                      {"index":1,"embedding":[0.0,2.0]},
                      {"index":0,"embedding":[3.0,4.0]}
                    ]}""");
        });
        EmbeddingProperties properties = properties();
        properties.setApiKey("chave-teste");
        properties.setDocumentPrefix("passage: ");

        List<float[]> vectors = client(properties).embedDocuments(List.of("dano moral", "negativacao"));

        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().path("model").asString()).isEqualTo("modelo-teste");
        assertThat(requests.getFirst().path("input").get(0).asString()).isEqualTo("passage: dano moral");
        assertThat(authorization.get()).isEqualTo("Bearer chave-teste");
        assertThat(vectors.get(0)).containsExactly(new float[] {0.6f, 0.8f}, within(1e-6f));
        assertThat(vectors.get(1)).containsExactly(new float[] {0.0f, 1.0f}, within(1e-6f));
    }

    @Test
    void splitsDocumentsIntoBatchesAndUsesQueryPrefixForSearches() throws Exception {
        List<Integer> batchSizes = new CopyOnWriteArrayList<>();
        AtomicReference<String> lastInput = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>("nao-chamado");
        start(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            JsonNode input = json.readTree(exchange.getRequestBody().readAllBytes()).path("input");
            batchSizes.add(input.size());
            lastInput.set(input.get(0).asString());
            var data = new StringBuilder("{\"data\":[");
            for (int i = 0; i < input.size(); i++) {
                if (i > 0) data.append(',');
                data.append("{\"index\":").append(i).append(",\"embedding\":[1.0,0.0]}");
            }
            respond(exchange, 200, data.append("]}").toString());
        });
        EmbeddingProperties properties = properties();
        properties.setBatchSize(2);
        properties.setQueryPrefix("query: ");
        var client = client(properties);

        assertThat(client.embedDocuments(List.of("a", "b", "c"))).hasSize(3);
        assertThat(batchSizes).containsExactly(2, 1);
        assertThat(authorization.get()).isNull();

        assertThat(client.embedQuery("colisao de veiculo")).containsExactly(1.0f, 0.0f);
        assertThat(lastInput.get()).isEqualTo("query: colisao de veiculo");
    }

    @Test
    void retriesTransientFailures() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        start(exchange -> {
            exchange.getRequestBody().readAllBytes();
            if (calls.incrementAndGet() == 1) respond(exchange, 503, "indisponivel");
            else respond(exchange, 200, "{\"data\":[{\"index\":0,\"embedding\":[0.0,5.0]}]}");
        });
        EmbeddingProperties properties = properties();
        properties.setMaxRetries(1);

        assertThat(client(properties).embedQuery("texto")).containsExactly(0.0f, 1.0f);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void reportsClientErrorsWithoutRetrying() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        start(exchange -> {
            exchange.getRequestBody().readAllBytes();
            calls.incrementAndGet();
            respond(exchange, 400, "modelo inexistente");
        });
        EmbeddingProperties properties = properties();
        properties.setMaxRetries(2);

        assertThatThrownBy(() -> client(properties).embedQuery("texto"))
                .isInstanceOf(EmbeddingProviderException.class)
                .extracting(exception -> ((EmbeddingProviderException) exception).getStatusCode())
                .isEqualTo(400);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void rejectsVectorsWithUnexpectedDimensions() throws Exception {
        start(exchange -> {
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 200, "{\"data\":[{\"index\":0,\"embedding\":[1.0,0.0,0.0]}]}");
        });
        EmbeddingProperties properties = properties();
        properties.setDimensions(2);

        assertThatThrownBy(() -> client(properties).embedQuery("texto"))
                .isInstanceOf(EmbeddingProviderException.class)
                .hasMessageContaining("3 dimensoes");
    }

    @Test
    void rejectsResponsesMissingVectors() throws Exception {
        start(exchange -> {
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 200, "{\"data\":[{\"index\":0,\"embedding\":[1.0]}]}");
        });

        assertThatThrownBy(() -> client(properties()).embedDocuments(List.of("a", "b")))
                .isInstanceOf(EmbeddingProviderException.class)
                .hasMessageContaining("1 vetores para 2 textos");
    }

    @Test
    void refusesToCallTheProviderWhileDisabled() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setModel("modelo-teste");
        properties.setBaseUrl("http://127.0.0.1:1");

        assertThatThrownBy(() -> client(properties).embedQuery("texto"))
                .isInstanceOf(EmbeddingProviderException.class)
                .hasMessageContaining("desabilitada");
    }

    private void start(Handler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
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

    private EmbeddingProperties properties() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        properties.setModel("modelo-teste");
        properties.setReadTimeout(Duration.ofSeconds(2));
        properties.setMaxRetries(0);
        properties.setRetryDelay(Duration.ZERO);
        return properties;
    }

    private OpenAiCompatibleEmbeddingClient client(EmbeddingProperties properties) {
        return new OpenAiCompatibleEmbeddingClient(properties, HttpClient.newHttpClient(), json);
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
