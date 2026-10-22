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
    private final ObjectReader recordReader;
    public StjAdapter(ObjectMapper json, StjCatalog catalog) {
        this.json = json; this.catalog = catalog;
        this.recordReader = json.readerFor(JsonNode.class).without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
    public DocumentSource source() { return DocumentSource.STJ; }
    public SourcePage collect(DocumentImportRequest r, AuditedFetch fetch) {
        var full = r.fullCollection() ? catalog.currentDecision(r,fetch) : null;
        JsonNode resource = full == null ? catalog.select(catalog.resources(r.datasetName(), fetch), r.recursoId(), "JSON", null)
                : full.resource();
        String url = StjCatalog.download(resource);
        boolean zip = "ZIP".equalsIgnoreCase(resource.path("format").asText());
        AuditedFetch.Payload payload = null;
        Selection selection;
        try {
            payload = fetch.get(url, zip ? "ZIP_BASE64" : "JSON");
            selection = zip ? readArchive(payload.texto(),r.start(),r.size()) : readJson(payload.texto(),r.start(),r.size());
        } catch (RuntimeException exception) {
            boolean unsupported = exception instanceof IllegalArgumentException;
            for (Throwable cause=exception;cause!=null;cause=cause.getCause()) {
                if (cause.getMessage()!=null && cause.getMessage().contains("excede 32 MiB")) unsupported=true;
            }
            if (full == null || !unsupported) throw exception;
            return new SourcePage(java.util.List.of(),payload == null ? null : payload.registroBrutoId(),
                    catalog.nextDecision(full,fetch),java.util.List.of("Arquivo STJ não importado: "
                    + resource.path("name").asText() + "; " + exception.getMessage()),1);
        }
        var rows = selection.rows;
        rows.forEach(n -> ((tools.jackson.databind.node.ObjectNode)n).put("_wikilaw_url",url));
        return new SourcePage(rows, payload.registroBrutoId(), selection.more
            ? new SourcePage.Continuacao(null,r.start()+rows.size(),null,required(resource,"id"), full == null ? null : full.dataset())
            : full == null ? null : catalog.nextDecision(full,fetch));
    }

    // Only materialize this page, including when a historical ZIP expands far
    // beyond the size of its compressed snapshot. Earlier records are skipped.
    private static class Selection {
        final int start, size;
        final java.util.List<JsonNode> rows = new java.util.ArrayList<>();
        int index;
        boolean more;
        Selection(int start,int size) { this.start=start; this.size=size; }
    }

    private void readArray(tools.jackson.core.JsonParser parser,Selection selection) {
        if (parser.nextToken()!=tools.jackson.core.JsonToken.START_ARRAY)
            throw new IllegalArgumentException("STJ.acordaos deve ser um array JSON");
        for (var token=parser.nextToken();token!=tools.jackson.core.JsonToken.END_ARRAY;token=parser.nextToken()) {
            if (token==null) throw new IllegalArgumentException("Array STJ incompleto");
            if (selection.index<selection.start) parser.skipChildren();
            else if (selection.rows.size()<selection.size) selection.rows.add(recordReader.readValue(parser));
            else { selection.more=true; return; }
            selection.index++;
        }
    }

    private Selection readJson(String text,int start,int size) {
        var selection = new Selection(start,size);
        try (var parser=json.createParser(text)) { readArray(parser,selection); }
        return selection;
    }

    private Selection readArchive(String base64,int start,int size) {
        var selection = new Selection(start,size);
        int files = 0;
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(
                java.util.Base64.getDecoder().decode(base64)))) {
            for (var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {
                if (entry.isDirectory() || !entry.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".json")) continue;
                files++;
                // Closing a parser must not close the archive before its next entry.
                var input = new java.io.FilterInputStream(zip) { @Override public void close() {} };
                try (var parser=json.createParser(input)) { readArray(parser,selection); }
                if (selection.more) return selection;
            }
            if (files==0) throw new IllegalArgumentException("ZIP STJ não contém arquivos JSON");
            return selection;
        } catch (java.io.IOException | tools.jackson.core.JacksonException exception) {
            throw new IllegalArgumentException("ZIP STJ inválido",exception);
        }
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
