package forge.wikilaw.backend.integration.datajud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import forge.wikilaw.backend.exception.DataJudHttpException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DataJudClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void returnsRawBodyAndSendsRequiredHeaders() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api_publica_tjsp/_search", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"hits\":{\"hits\":[]}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        DataJudClient client = clientForServer();
        DataJudRawResponse response = client.search(DataJudTribunal.TJSP, "{\"query\":{\"match_all\":{}}}");

        assertThat(response.payload()).isEqualTo("{\"hits\":{\"hits\":[]}}");
        assertThat(authorization.get()).isEqualTo("APIKey public-test-key");
    }

    @Test
    void reportsHttpErrorWithoutRetryingClientErrors() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api_publica_tjsp/_search", exchange -> {
            byte[] body = "bad request".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        DataJudClient client = clientForServer();

        assertThatThrownBy(() -> client.search(DataJudTribunal.TJSP, "{}"))
                .isInstanceOf(DataJudHttpException.class)
                .extracting(exception -> ((DataJudHttpException) exception).getStatusCode())
                .isEqualTo(400);
    }

    private DataJudClient clientForServer() {
        DataJudProperties properties = new DataJudProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setApiKey("public-test-key");
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(2));
        properties.setMaxRetries(0);
        properties.setRetryDelay(Duration.ZERO);
        return new DataJudClient(properties, HttpClient.newHttpClient());
    }
}
