package forge.wikilaw.backend.integration.stj;

import forge.wikilaw.backend.integration.documents.*;
import static forge.wikilaw.backend.integration.documents.DocumentFields.*;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;

@Component
public class StjCatalog {
    private final ObjectMapper json;
    public StjCatalog(ObjectMapper json) { this.json = json; }
    public List<JsonNode> resources(String dataset, AuditedFetch fetch) {
        var payload = fetch.get("https://dadosabertos.web.stj.jus.br/api/3/action/package_show?id=" + enc(dataset), "JSON");
        JsonNode root = json.readTree(payload.texto());
        if (!root.path("success").asBoolean()) throw new IllegalArgumentException("Catálogo STJ não retornou sucesso");
        return array(root.path("result").path("resources"), "STJ.resources");
    }
    public JsonNode select(List<JsonNode> resources, String id, String format, String name) {
        return resources.stream().filter(n -> format.equalsIgnoreCase(value(n, "format")))
            .filter(n -> name == null || name.equalsIgnoreCase(value(n, "name")))
            .filter(n -> id == null || id.equals(value(n, "id")))
            .max(Comparator.comparing(n -> n.path("name").asText("")))
            .orElseThrow(() -> new IllegalArgumentException("Recurso STJ não encontrado no dataset/formato selecionado"));
    }
    public static String download(JsonNode resource) {
        String url = required(resource, "url");
        java.net.URI uri = java.net.URI.create(url);
        if (!"https".equals(uri.getScheme()) || !"dadosabertos.web.stj.jus.br".equals(uri.getHost()))
            throw new IllegalArgumentException("Download não pertence ao portal oficial STJ");
        return url;
    }
}
