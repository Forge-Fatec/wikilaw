package forge.wikilaw.backend.integration.pangea;

import forge.wikilaw.backend.integration.documents.*;
import static forge.wikilaw.backend.integration.documents.DocumentFields.*;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;

/** Contrato observado no frontend oficial e validado contra a pesquisa pública. */
@Component
public class PangeaAdapter implements DocumentAdapter {
    private static final String BASE = "https://pangeabnp.pdpj.jus.br";
    private final ObjectMapper json;

    public PangeaAdapter(ObjectMapper json) { this.json = json; }
    public DocumentSource source() { return DocumentSource.PANGEA; }

    public SourcePage collect(DocumentImportRequest r, AuditedFetch fetch) {
        var parameters = fetch.get(BASE + "/api/v1/parametros", "JSON");
        JsonNode catalog = json.readTree(parameters.texto());
        // O portal envia as opções do catálogo; não pressupor que lista vazia signifique todas.
        List<String> organs = codes(catalog.path("orgaos"), "orgaos");
        List<String> types = codes(catalog.path("especies"), "especies");
        String organ = r.orgao()==null ? organs.getFirst() : r.orgao();
        String type = r.tipo()==null ? types.getFirst() : r.tipo();
        if (r.fullCollection() && (!organs.contains(organ) || !types.contains(type)))
            throw new IllegalArgumentException("Partição Pangea fora do catálogo");
        Map<String,Object> filter = new LinkedHashMap<>();
        filter.put("buscaGeral", r.query());
        for (String field : List.of("todasPalavras", "quaisquerPalavras", "semPalavras",
                "trechoExato", "atualizacaoDesde", "atualizacaoAte", "nr")) filter.put(field, "");
        filter.put("orgaos", r.fullCollection() ? List.of(organ) : organs);
        filter.put("tipos", r.fullCollection() ? List.of(type) : types);
        filter.put("cancelados", r.fullCollection());
        filter.put("ordenacao", "Text");
        filter.put("pagina", r.page() + 1); // WikiLaw: zero-based; Pangea: one-based.
        filter.put("tamanhoPagina", r.size());
        var payload = fetch.request(BASE + "/api/v1/precedentes",
            json.writeValueAsString(Map.of("filtro", filter)), "JSON");
        JsonNode root = json.readTree(payload.texto());
        var rows = array(root.path("resultados"), "Pangea.resultados");
        if (!root.path("total").isIntegralNumber() || root.path("total").asLong() < 0)
            throw new IllegalArgumentException("Resposta Pangea sem total válido");
        if (rows.size() > r.size()) throw new IllegalArgumentException("Pangea excedeu o tamanho da página solicitado");
        boolean more = r.fullCollection() ? rows.size()==r.size()
                : ((long) r.page() + 1) * r.size() < root.path("total").asLong();
        if (r.fullCollection()) {
            var next = more ? new SourcePage.Continuacao(r.page()+1,null,null,null,null,organ,type)
                    : nextPartition(organs,types,organ,type);
            return new SourcePage(rows,payload.registroBrutoId(),next);
        }
        return new SourcePage(rows, payload.registroBrutoId(),
            more && !rows.isEmpty() ? new SourcePage.Continuacao(r.page()+1, null, null, null) : null);
    }

    private SourcePage.Continuacao nextPartition(List<String> organs,List<String> types,String organ,String type) {
        int nextType=types.indexOf(type)+1;
        if (nextType<types.size()) return new SourcePage.Continuacao(0,null,null,null,null,organ,types.get(nextType));
        int nextOrgan=organs.indexOf(organ)+1;
        if (nextOrgan<organs.size()) return new SourcePage.Continuacao(0,null,null,null,null,organs.get(nextOrgan),types.getFirst());
        return null;
    }

    private List<String> codes(JsonNode node, String field) {
        List<String> result = array(node, "Pangea." + field).stream()
            .map(n -> required(n, "sigla")).distinct().toList();
        if (result.isEmpty()) throw new IllegalArgumentException("Catálogo Pangea vazio: " + field);
        return result;
    }

    public NormalizedDocument normalize(JsonNode n) {
        String id = required(n, "id");
        String organ = required(n, "orgao");
        String type = required(n, "tipo");
        String number = required(n, "nr");
        if (!organ.matches("[A-Z0-9]{2,20}")) throw new IllegalArgumentException("Órgão Pangea inválido");
        if (n.path("segredoJustica").asBoolean(false))
            throw new UnavailableDocumentException(id, "Registro Pangea marcado como sigiloso");
        return NormalizedDocument.builder().identificador(id)
            .titulo(organ + " — " + type + " nº " + number)
            .tribunal(organ).tipo(type).numeroTema(number)
            .resumo(plain(value(n,"questao"))).tese(plain(value(n,"tese")))
            .situacao(value(n,"situacao"))
            // ultimaAtualizacao não é data de julgamento/publicação.
            // Processos paradigma podem conter descrições e links, não números CNJ.
            .url(BASE + "/pesquisa?orgao=" + enc(organ.toLowerCase(Locale.ROOT))
                + "&tipo=" + enc(type) + "&nr=" + enc(number))
            .metadados(json.writeValueAsString(n)).build();
    }
}
