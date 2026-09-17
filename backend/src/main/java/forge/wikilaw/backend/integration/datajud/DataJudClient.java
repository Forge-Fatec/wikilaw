package forge.wikilaw.backend.integration.datajud;

import forge.wikilaw.backend.exception.DataJudHttpException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DataJudClient {

    private static final Logger log = LoggerFactory.getLogger(DataJudClient.class);

    private final DataJudProperties properties;
    private final HttpClient httpClient;

    @Autowired
    public DataJudClient(DataJudProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
    }

    DataJudClient(DataJudProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    public DataJudRawResponse search(DataJudTribunal tribunal, String queryBody) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new DataJudHttpException("DATAJUD_API_KEY não configurada", 0);
        }

        URI endpoint = URI.create(stripTrailingSlash(properties.getBaseUrl())
                + "/api_publica_" + tribunal.alias() + "/_search");
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(properties.getReadTimeout())
                .header("Authorization", "APIKey " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(queryBody))
                .build();

        int maxAttempts = Math.max(1, properties.getMaxRetries() + 1);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                log.debug("Consultando DataJud tribunal={} tentativa={}/{}", tribunal, attempt, maxAttempts);
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return new DataJudRawResponse(response.body());
                }
                if (!isRetryable(status) || attempt == maxAttempts) {
                    throw new DataJudHttpException("DataJud respondeu com HTTP " + status, status);
                }
                log.warn("Falha transitória do DataJud tribunal={} status={} tentativa={}/{}",
                        tribunal, status, attempt, maxAttempts);
            } catch (IOException exception) {
                if (attempt == maxAttempts) {
                    throw new DataJudHttpException("Falha de comunicação com o DataJud", exception);
                }
                log.warn("Falha de I/O no DataJud tribunal={} tentativa={}/{}: {}",
                        tribunal, attempt, maxAttempts, exception.getMessage());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new DataJudHttpException("Consulta ao DataJud interrompida", exception);
            }
            waitBeforeRetry(attempt);
        }
        throw new DataJudHttpException("Consulta ao DataJud falhou", 0);
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
            throw new DataJudHttpException("Espera de retry do DataJud interrompida", exception);
        }
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
