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

    public List<String> decisionDatasets(AuditedFetch fetch) {
        var payload = fetch.get("https://dadosabertos.web.stj.jus.br/api/3/action/package_list", "JSON");
        var root = json.readTree(payload.texto());
        if (!root.path("success").asBoolean()) throw new IllegalArgumentException("Catálogo STJ não retornou sucesso");
        return array(root.path("result"),"STJ.datasets").stream().map(JsonNode::asText)
                .filter(name -> name.startsWith("espelhos-de-acordaos-")).sorted().toList();
    }

    private List<JsonNode> decisionResources(String dataset, AuditedFetch fetch) {
        return resources(dataset,fetch).stream()
                .filter(n -> Set.of("JSON","ZIP").contains(n.path("format").asText().toUpperCase(Locale.ROOT)))
                .sorted(Comparator.comparing((JsonNode n) -> n.path("name").asText())
                        .thenComparing(n -> n.path("id").asText())).toList();
    }

    public record DecisionResource(String dataset, JsonNode resource) {}

    public DecisionResource currentDecision(DocumentImportRequest r, AuditedFetch fetch) {
        List<String> datasets = decisionDatasets(fetch);
        String dataset = r.dataset() == null ? datasets.stream().findFirst().orElseThrow(
                () -> new IllegalArgumentException("Catálogo STJ sem datasets de acórdãos")) : r.dataset();
        int start = datasets.indexOf(dataset);
        if (start < 0) throw new IllegalArgumentException("Dataset STJ fora do catálogo de acórdãos");
        for (int i=start;i<datasets.size();i++) {
            var files = decisionResources(datasets.get(i),fetch);
            if (r.recursoId()!=null && i==start) {
                return new DecisionResource(dataset,files.stream().filter(n -> r.recursoId().equals(value(n,"id")))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("Recurso STJ não encontrado")));
            }
            if (!files.isEmpty()) return new DecisionResource(datasets.get(i),files.getFirst());
        }
        throw new IllegalArgumentException("Catálogo STJ sem arquivos JSON/ZIP acessíveis");
    }

    public SourcePage.Continuacao nextDecision(DecisionResource current, AuditedFetch fetch) {
        var files = decisionResources(current.dataset(),fetch);
        for (int i=0;i<files.size();i++) {
            if (required(files.get(i),"id").equals(required(current.resource(),"id")) && i+1<files.size()) {
                return new SourcePage.Continuacao(null,0,null,required(files.get(i+1),"id"),current.dataset());
            }
        }
        var datasets = decisionDatasets(fetch);
        for (int i=datasets.indexOf(current.dataset())+1;i<datasets.size();i++) {
            var next = decisionResources(datasets.get(i),fetch);
            if (!next.isEmpty()) return new SourcePage.Continuacao(null,0,null,required(next.getFirst(),"id"),datasets.get(i));
        }
        return null;
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
