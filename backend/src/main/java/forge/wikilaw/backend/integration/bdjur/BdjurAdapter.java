package forge.wikilaw.backend.integration.bdjur;

import forge.wikilaw.backend.integration.documents.*;
import static forge.wikilaw.backend.integration.documents.DocumentFields.*;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;

@Component
public class BdjurAdapter implements DocumentAdapter {
    private final ObjectMapper json;
    public BdjurAdapter(ObjectMapper json) { this.json = json; }
    public DocumentSource source() { return DocumentSource.BDJUR; }
    public SourcePage collect(DocumentImportRequest r, AuditedFetch fetch) {
        var response = fetch.get("https://bdjur.stj.jus.br/server/api/discover/search/objects?query="
            + enc(r.query()) + "&size=" + r.size() + "&page=" + r.page(), "JSON");
        JsonNode result = json.readTree(response.texto()).path("_embedded").path("searchResult");
        JsonNode objects = result.path("_embedded").path("objects");
        var rows = new ArrayList<JsonNode>();
        if (objects.isArray()) {
            for (JsonNode hit : objects) rows.add(hit.path("_embedded").path("indexableObject"));
        } else if (result.path("page").path("totalElements").asLong(-1) != 0) {
            throw new IllegalArgumentException("Resposta incompatível: BDJur.searchResult");
        }
        boolean more = result.path("page").path("totalPages").asInt(r.page()+1) > r.page()+1;
        return new SourcePage(rows, response.registroBrutoId(), more ? new SourcePage.Continuacao(r.page()+1,null,null,null) : null);
    }
    private String metadata(JsonNode n, String field) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : n.path("metadata").path(field)) values.add(value(item, "value"));
        return join(values);
    }
    public NormalizedDocument normalize(JsonNode n) {
        if (n.path("withdrawn").asBoolean(false) || !n.path("discoverable").asBoolean(true))
            throw new UnavailableDocumentException(required(n,"uuid"),"Item BDJur retirado ou não público");
        String id = required(n, "uuid");
        String title = metadata(n, "dc.title");
        if (title.isBlank()) title = required(n, "name");
        String date = metadata(n, "dc.date.issued");
        String summary = metadata(n, "dc.description.abstract");
        if (summary.isBlank()) summary = metadata(n, "dc.description");
        String url = metadata(n, "dc.identifier.uri");
        if (url.isBlank()) url = "https://bdjur.stj.jus.br/handle/" + required(n, "handle");
        return NormalizedDocument.builder().identificador(id).titulo(plain(title))
            .tipo(metadata(n, "dc.type")).resumo(plain(summary))
            .autores(metadata(n, "dc.contributor.author")).idioma(metadata(n, "dc.language.iso"))
            .palavrasChave(metadata(n, "dc.subject")).doi(metadata(n, "dc.identifier.doi"))
            .dataOriginal(date).dataPublicacao(date(date)).url(url)
            .metadados(json.writeValueAsString(n.path("metadata"))).build();
    }
}
