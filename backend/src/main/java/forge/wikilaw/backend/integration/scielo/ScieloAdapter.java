package forge.wikilaw.backend.integration.scielo;

import forge.wikilaw.backend.integration.documents.*;
import static forge.wikilaw.backend.integration.documents.DocumentFields.*;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;

@Component
public class ScieloAdapter implements DocumentAdapter {
    private final ObjectMapper json;
    public ScieloAdapter(ObjectMapper json) { this.json = json; }
    public DocumentSource source() { return DocumentSource.SCIELO; }
    public SourcePage collect(DocumentImportRequest r, AuditedFetch fetch) {
        String url = "https://articlemeta.scielo.org/api/v1/articles/?collection=scl&issn=" + enc(r.journal())
            + "&limit=" + r.size() + "&offset=" + r.start() + "&body=false";
        if (r.desde() != null) url += "&from=" + enc(r.desde());
        if (r.ate() != null) url += "&until=" + enc(r.ate());
        var response = fetch.get(url, "JSON");
        JsonNode root = json.readTree(response.texto());
        var rows = array(root.path("objects"), "SciELO.objects");
        long total = root.path("meta").path("total").asLong(-1);
        boolean more = total < 0 ? rows.size() == r.size() : (long) r.start() + rows.size() < total;
        return new SourcePage(rows, response.registroBrutoId(),
            more && !rows.isEmpty() ? new SourcePage.Continuacao(null, r.start()+rows.size(),null,null) : null);
    }
    private String first(JsonNode n, String field, String subfield) {
        JsonNode entries = n.path(field);
        if (!entries.isArray()) return null;
        JsonNode chosen = entries.path(0);
        for (JsonNode entry : entries) if ("pt".equals(value(entry, "l"))) { chosen = entry; break; }
        return value(chosen, subfield);
    }
    public NormalizedDocument normalize(JsonNode n) {
        String id = required(n, "code");
        JsonNode a = n.path("article");
        String title = first(a, "v12", "_");
        if (title == null) title = first(a, "v977", "_");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Artigo sem título");
        List<String> authors = new ArrayList<>();
        for (JsonNode author : a.path("v10")) authors.add(join(Arrays.asList(value(author,"n"),value(author,"s"))).replace("; ", " "));
        List<String> keywords = new ArrayList<>();
        for (JsonNode keyword : a.path("v85")) keywords.add(value(keyword,"k"));
        String doi = first(a, "v237", "_");
        String issued = first(a, "v65", "_");
        return NormalizedDocument.builder().identificador(id).titulo(plain(title)).tipo("ARTIGO")
            .resumo(plain(first(a,"v83","a"))).autores(join(authors)).palavrasChave(join(keywords))
            .periodico(first(a,"v30","_")).issn(first(a,"v35","_")).idioma(first(a,"v40","_"))
            .dataOriginal(issued).dataPublicacao(date(issued)).doi(doi)
            .url(doi != null && doi.startsWith("10.") ? "https://doi.org/" + doi
                : "https://www.scielo.br/scielo.php?script=sci_arttext&pid=" + enc(id))
            .metadados(json.writeValueAsString(a)).build();
    }
}
