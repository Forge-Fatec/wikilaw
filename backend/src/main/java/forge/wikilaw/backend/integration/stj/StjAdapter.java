package forge.wikilaw.backend.integration.stj;

import forge.wikilaw.backend.integration.documents.*;
import static forge.wikilaw.backend.integration.documents.DocumentFields.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;

@Component
public class StjAdapter implements DocumentAdapter {
    private final ObjectMapper json;
    private final StjCatalog catalog;
    public StjAdapter(ObjectMapper json, StjCatalog catalog) { this.json = json; this.catalog = catalog; }
    public DocumentSource source() { return DocumentSource.STJ; }
    public SourcePage collect(DocumentImportRequest r, AuditedFetch fetch) {
        JsonNode resource = catalog.select(catalog.resources(r.datasetName(), fetch), r.recursoId(), "JSON", null);
        String url = StjCatalog.download(resource);
        var payload = fetch.get(url, "JSON");
        var all = array(json.readTree(payload.texto()), "STJ.acordaos");
        int from = Math.min(r.start(), all.size());
        int to = Math.min(from + r.size(), all.size());
        var rows = all.subList(from, to);
        rows.forEach(n -> ((tools.jackson.databind.node.ObjectNode)n).put("_wikilaw_url",url));
        return new SourcePage(rows, payload.registroBrutoId(), to < all.size()
            ? new SourcePage.Continuacao(null,to,null,required(resource,"id")) : null);
    }
    public NormalizedDocument normalize(JsonNode n) {
        String pub = value(n, "dataPublicacao");
        java.time.LocalDate pubDate = null;
        if (pub != null) {
            var matcher = java.util.regex.Pattern.compile("[0-9]{2}/[0-9]{2}/[0-9]{4}").matcher(pub);
            if (matcher.find()) pubDate = date(matcher.group());
        }
        return NormalizedDocument.builder().identificador(required(n,"id"))
            .titulo("STJ — " + required(n,"siglaClasse") + " " + required(n,"numeroProcesso"))
            .tipo(value(n,"tipoDeDecisao")).resumo(value(n,"ementa")).decisao(value(n,"decisao"))
            .numeroProcesso(value(n,"numeroProcesso")).relator(value(n,"ministroRelator"))
            .orgaoJulgador(value(n,"nomeOrgaoJulgador")).dataJulgamento(date(value(n,"dataDecisao")))
            .dataPublicacao(pubDate).dataOriginal(pub)
            .url(publicDecisionUrl(value(n,"siglaClasse"), value(n,"numeroProcesso")))
            .metadados(json.writeValueAsString(n)).build();
    }

    private String publicDecisionUrl(String courtClass, String number) {
        if (courtClass != null && !courtClass.isBlank() && number != null && !number.isBlank()) {
            String numberDigits = number.replaceAll("\\D+", "");
            if (numberDigits.isBlank()) return "https://scon.stj.jus.br/SCON/jurisprudencia/";
            String query = "(" + courtClass.trim().toUpperCase(java.util.Locale.ROOT)
                + " INPATH(CLAS) AND " + numberDigits + " INPATH(NUM))";
            return "https://scon.stj.jus.br/SCON/jurisprudencia/toc.jsp?livre="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);
        }
        return "https://scon.stj.jus.br/SCON/jurisprudencia/";
    }
}
