package forge.wikilaw.backend.integration.documents;

import java.util.List;
import tools.jackson.databind.JsonNode;

public record SourcePage(List<JsonNode> registros, Long registroBrutoId, Continuacao proxima, List<String> avisos, int falhasColeta) {
    public SourcePage(List<JsonNode> registros, Long registroBrutoId, Continuacao proxima, List<String> avisos) {
        this(registros,registroBrutoId,proxima,avisos,0);
    }
    public SourcePage(List<JsonNode> registros, Long registroBrutoId, Continuacao proxima) {
        this(registros, registroBrutoId, proxima, List.of());
    }
    public record Continuacao(Integer pagina, Integer offset, String resumptionToken, String recursoId, String dataset,
            String orgao, String tipo) {
        public Continuacao(Integer pagina, Integer offset, String resumptionToken, String recursoId, String dataset) {
            this(pagina,offset,resumptionToken,recursoId,dataset,null,null);
        }
        public Continuacao(Integer pagina, Integer offset, String resumptionToken, String recursoId) {
            this(pagina,offset,resumptionToken,recursoId,null);
        }
    }
}
