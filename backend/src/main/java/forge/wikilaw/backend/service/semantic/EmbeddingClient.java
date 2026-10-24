package forge.wikilaw.backend.service.semantic;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** Talks only to the configured internal inference service; user text is never logged. */
@Component
public class EmbeddingClient {
    public static final String MODEL = "intfloat/multilingual-e5-small@614241f622f53c4eeff9890bdc4f31cfecc418b3:windows-v1";
    public static final int DIMENSIONS = 384;
    private final URI endpoint;
    private final ObjectMapper json;
    private final HttpClient http;

    public EmbeddingClient(ObjectMapper json, @Value("${wikilaw.vector.service-url:http://localhost:8000}") String url) {
        this.json = json;
        this.endpoint = URI.create(url.replaceAll("/+$", "") + "/embed");
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public List<float[]> embed(List<String> texts, String kind) {
        if (texts.isEmpty()) return List.of();
        var result = new ArrayList<float[]>();
        for (int offset = 0; offset < texts.size(); offset += 8) {
            var batch = texts.subList(offset, Math.min(offset + 8, texts.size()));
            try {
                var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(120))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("texts", batch, "kind", kind)))).build();
                var response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw unavailable();
                var body = json.readValue(response.body(), EmbeddingResponse.class);
                if (!MODEL.equals(body.model()) || body.dimensions() != DIMENSIONS || body.vectors() == null
                        || body.vectors().size() != batch.size()) throw unavailable();
                for (float[] vector : body.vectors()) {
                    double norm = 0;
                    if (vector == null || vector.length != DIMENSIONS) throw unavailable();
                    for (float value : vector) {
                        if (!Float.isFinite(value)) throw unavailable();
                        norm += value * value;
                    }
                    if (norm < 0.0001) throw unavailable();
                    result.add(vector);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt(); throw unavailable();
            } catch (Exception exception) { throw unavailable(); }
        }
        return List.copyOf(result);
    }

    public static String literal(float[] vector) {
        var values = new StringJoiner(",", "[", "]");
        for (float value : vector) values.add(Float.toString(value));
        return values.toString();
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "A busca por similaridade está indisponível no momento. Tente novamente ou escolha a busca textual.");
    }
    public record EmbeddingResponse(String model, int dimensions, List<float[]> vectors) {}
}
