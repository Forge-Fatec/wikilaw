package forge.wikilaw.backend.integration.stj;

import forge.wikilaw.backend.integration.documents.*;
import static forge.wikilaw.backend.integration.documents.DocumentFields.*;
import java.io.*;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;
import tools.jackson.databind.node.ObjectNode;

@Component
public class StjPrecedentesAdapter implements DocumentAdapter {
    private final ObjectMapper json;
    private final StjCatalog catalog;
    public StjPrecedentesAdapter(ObjectMapper json, StjCatalog catalog) { this.json = json; this.catalog = catalog; }
    public DocumentSource source() { return DocumentSource.STJ_PRECEDENTES; }

    public List<JsonNode> parseCsv(String csv) {
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true).get().parse(new StringReader(csv.replaceFirst("^\\uFEFF","")))) {
            if (!parser.getHeaderMap().containsKey("sequencialPrecedente"))
                throw new IllegalArgumentException("CSV STJ sem sequencialPrecedente");
            List<JsonNode> rows = new ArrayList<>();
            for (CSVRecord row : parser) rows.add(json.valueToTree(row.toMap()));
            return rows;
        } catch (IOException | UncheckedIOException e) { throw new IllegalArgumentException("CSV STJ inválido",e); }
    }
    public SourcePage collect(DocumentImportRequest r, AuditedFetch fetch) {
        var resources = catalog.resources("precedentes-qualificados", fetch);
        JsonNode temas = catalog.select(resources,r.recursoId(),"CSV","Temas.csv");
        String url = StjCatalog.download(temas);
        var payload = fetch.get(url, "CSV");
        var all = parseCsv(payload.texto());
        int from = Math.min(r.start(),all.size());
        int to = Math.min(from+r.size(),all.size());
        var rows = all.subList(from,to);
        JsonNode processos = catalog.select(resources,null,"CSV","Processos.csv");
        var linked = fetch.get(StjCatalog.download(processos),"CSV");
        Map<String,List<JsonNode>> byTheme = new HashMap<>();
        int unlinked = 0;
        for (JsonNode n : parseCsv(linked.texto())) {
            String sequence = value(n,"sequencialPrecedente");
            String registration = value(n,"numeroRegistro");
            if (sequence == null || sequence.isBlank() || registration == null || registration.isBlank()) {
                unlinked++;
                continue; // Linhas incompletas existem no CSV oficial; permanecem no bruto.
            }
            byTheme.computeIfAbsent(sequence, k -> new ArrayList<>()).add(n);
        }
        for (JsonNode row : rows) {
            ObjectNode item = (ObjectNode) row;
            item.put("_wikilaw_url",url);
            item.put("_registro_processos",linked.registroBrutoId());
            item.set("_processos",json.valueToTree(byTheme.getOrDefault(required(row,"sequencialPrecedente"),List.of())));
        }
        return new SourcePage(rows,payload.registroBrutoId(),to < all.size()
            ? new SourcePage.Continuacao(null,to,null,required(temas,"id")) : null,
            unlinked == 0 ? List.of() : List.of("Processos.csv contém " + unlinked
                + " linhas sem identificadores suficientes para vínculo; preservadas no registro bruto " + linked.registroBrutoId()));
    }
    public NormalizedDocument normalize(JsonNode n) {
        List<NormalizedDocument.RelatedProcess> linked = new ArrayList<>();
        for (JsonNode p : n.path("_processos")) linked.add(new NormalizedDocument.RelatedProcess(
            required(p,"numeroRegistro"),value(p,"Processo"),value(p,"ministroRelator"),
            value(p,"leadingCase"),n.path("_registro_processos").asLong()));
        return NormalizedDocument.builder().identificador(required(n,"sequencialPrecedente"))
            .titulo("STJ — " + required(n,"tipoPrecedente") + " " + required(n,"numeroPrecedente"))
            .tipo(value(n,"tipoPrecedente")).numeroTema(value(n,"numeroPrecedente"))
            .resumo(value(n,"questaoSubmetidaAJulgamento")).tese(value(n,"teseFirmada"))
            .situacao(value(n,"situacao")).dataJulgamento(date(value(n,"dataJulgamento")))
            .dataPublicacao(date(value(n,"dataPublicacaoAcordao"))).dataOriginal(value(n,"dataPublicacaoAcordao"))
            .url(value(n,"_wikilaw_url")).processosRelacionados(linked)
            .metadados(json.writeValueAsString(n)).build();
    }
}
