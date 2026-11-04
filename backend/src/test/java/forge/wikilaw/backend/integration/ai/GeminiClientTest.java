package forge.wikilaw.backend.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import forge.wikilaw.backend.exception.AiProviderException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class GeminiClientTest {

    private static final String PATH = "/v1beta/models/gemini-teste:generateContent";

    private final ObjectMapper json = JsonMapper.builder().build();
    private final JsonNode schema = json.createObjectNode().put("type", "OBJECT");
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsKeyInstructionsAndSchemaAndReturnsTheGeneratedJson() throws Exception {
        AtomicReference<JsonNode> request = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        start(exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            request.set(json.readTree(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, """
                    {"candidates":[{"finishReason":"STOP","content":{"parts":[
                      {"text":"pensando...","thought":true},
                      {"text":"{\\"resumo\\":"},{"text":"\\"Analise\\"}"}
                    ]}}]}""");
        });

        JsonNode result = client(properties()).generateJson("regras", "pergunta", schema);

        assertThat(result.path("resumo").asString()).isEqualTo("Analise");
        assertThat(apiKey.get()).isEqualTo("chave-teste");
        JsonNode body = request.get();
        assertThat(body.path("systemInstruction").path("parts").path(0).path("text").asString()).isEqualTo("regras");
        assertThat(body.path("contents").path(0).path("parts").path(0).path("text").asString()).isEqualTo("pergunta");
        assertThat(body.path("generationConfig").path("responseMimeType").asString()).isEqualTo("application/json");
        assertThat(body.path("generationConfig").path("responseSchema")).isEqualTo(schema);
        assertThat(body.path("generationConfig").path("maxOutputTokens").asInt()).isEqualTo(8192);
        assertThat(body.path("generationConfig").path("thinkingConfig").path("thinkingBudget").asInt()).isEqualTo(2048);
    }

    @Test
    void letsTheModelChooseTheThinkingBudgetWhenSetToMinusOne() throws Exception {
        AtomicReference<JsonNode> request = new AtomicReference<>();
        start(exchange -> {
            request.set(json.readTree(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, """
                    {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"{\\"resumo\\":\\"ok\\"}"}]}}]}""");
        });
        GeminiProperties properties = properties();
        properties.setThinkingBudget(-1);

        client(properties).generateJson("r", "p", schema);

        assertThat(request.get().path("generationConfig").has("thinkingConfig")).isFalse();
    }

    @Test
    void rejectsAThinkingBudgetThatLeavesNoRoomForTheAnswer() {
        GeminiProperties properties = properties();
        properties.setThinkingBudget(8192);

        assertThatThrownBy(() -> client(properties).generateJson("r", "p", schema))
                .isInstanceOf(AiProviderException.class).hasMessageContaining("WIKILAW_GEMINI_THINKING_BUDGET");
    }

    @Test
    void retriesRateLimitAndServerErrors() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        start(exchange -> {
            if (calls.incrementAndGet() == 1) respond(exchange, 503, "{}");
            else respond(exchange, 200, """
                    {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"{\\"resumo\\":\\"ok\\"}"}]}}]}""");
        });

        assertThat(client(properties()).generateJson("r", "p", schema).path("resumo").asString()).isEqualTo("ok");
        assertThat(calls).hasValue(2);
    }

    @Test
    void reportsRateLimitWithItsStatusAfterTheLastAttempt() throws Exception {
        start(exchange -> respond(exchange, 429, "{}"));

        assertThatThrownBy(() -> client(properties()).generateJson("r", "p", schema))
                .isInstanceOf(AiProviderException.class)
                .extracting(error -> ((AiProviderException) error).getStatusCode()).isEqualTo(429);
    }

    @Test
    void rejectsBlockedTruncatedAndNonJsonAnswers() throws Exception {
        AtomicReference<String> answer = new AtomicReference<>();
        start(exchange -> respond(exchange, 200, answer.get()));
        GeminiClient client = client(properties());

        answer.set("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");
        assertThatThrownBy(() -> client.generateJson("r", "p", schema)).hasMessageContaining("SAFETY");

        answer.set("{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\",\"content\":{\"parts\":[{\"text\":\"{\"}]}}]}");
        assertThatThrownBy(() -> client.generateJson("r", "p", schema)).hasMessageContaining("MAX_TOKENS");

        answer.set("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"nao e json\"}]}}]}");
        assertThatThrownBy(() -> client.generateJson("r", "p", schema)).isInstanceOf(AiProviderException.class);
    }

    @Test
    void failsFastWhenDisabledOrWithoutKey() {
        GeminiProperties disabled = properties();
        disabled.setEnabled(false);
        assertThatThrownBy(() -> client(disabled).generateJson("r", "p", schema))
                .isInstanceOf(AiProviderException.class).hasMessageContaining("desabilitada");

        GeminiProperties withoutKey = properties();
        withoutKey.setApiKey(" ");
        assertThatThrownBy(() -> client(withoutKey).generateJson("r", "p", schema))
                .isInstanceOf(AiProviderException.class).hasMessageContaining("WIKILAW_GEMINI_API_KEY");
    }

    private GeminiClient client(GeminiProperties properties) {
        return new GeminiClient(properties, HttpClient.newHttpClient(), json);
    }

    private GeminiProperties properties() {
        GeminiProperties properties = new GeminiProperties();
        properties.setEnabled(true);
        properties.setApiKey("chave-teste");
        properties.setModel("gemini-teste");
        properties.setBaseUrl(server == null ? "http://127.0.0.1:1" : "http://127.0.0.1:" + server.getAddress().getPort() + "/");
        properties.setRetryDelay(Duration.ofMillis(1));
        properties.setMaxRetries(1);
        return properties;
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
