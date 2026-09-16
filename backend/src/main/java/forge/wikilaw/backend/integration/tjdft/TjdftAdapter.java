package forge.wikilaw.backend.integration.tjdft;

import forge.wikilaw.backend.integration.documents.*;
import static forge.wikilaw.backend.integration.documents.DocumentFields.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;
import java.util.Map;

@Component
public class TjdftAdapter implements DocumentAdapter {
    private final ObjectMapper json;
    public TjdftAdapter(ObjectMapper json) { this.json = json; }
    public DocumentSource source() { return DocumentSource.TJDFT; }
    public SourcePage collect(DocumentImportRequest r, AuditedFetch fetch) {
        var response = fetch.request("https://jurisdf.tjdft.jus.br/api/v1/pesquisa",
            json.writeValueAsString(Map.of("query", r.query(), "pagina", r.page(), "tamanho", r.size())), "JSON");
        JsonNode root = json.readTree(response.texto());
        var rows = array(root.path("registros"), "TJDFT.registros");
        long total = root.path("hits").isNumber() ? root.path("hits").asLong() : root.path("hits").path("value").asLong(-1);
        boolean more = total >= 0 ? ((long) r.page() + 1) * r.size() < total : rows.size() == r.size();
        return new SourcePage(rows, response.registroBrutoId(),
            more ? new SourcePage.Continuacao(r.page() + 1, null, null, null) : null);
    }
    public NormalizedDocument normalize(JsonNode n) {
        if (n.path("segredoJustica").asBoolean(false))
            throw new UnavailableDocumentException(required(n,"uuid"),"Registro marcado como sigiloso; indisponível na consulta");
        String id = required(n, "uuid");
        String inteiro = value(n, "inteiroTeorHtml");
        if (inteiro == null) inteiro = value(n, "inteiroTeor");
        if (inteiro != null && plain(inteiro).toLowerCase(java.util.Locale.ROOT).contains("inteiro teor indisponível")) inteiro = null;
        return NormalizedDocument.builder().identificador(id)
            .titulo("TJDFT — Acórdão " + required(n, "identificador")).tipo("ACORDAO")
            .resumo(value(n, "ementa")).numeroProcesso(value(n, "processo"))
            .relator(value(n, "nomeRelator")).orgaoJulgador(value(n, "descricaoOrgaoJulgador"))
            .dataJulgamento(date(value(n, "dataJulgamento")))
            .dataPublicacao(date(value(n, "dataPublicacao"))).dataOriginal(value(n, "dataPublicacao"))
            .decisao(value(n, "decisao")).inteiroTeor(plain(inteiro))
            .url("https://jurisdf.tjdft.jus.br/").metadados(json.writeValueAsString(n)).build();
    }
}
