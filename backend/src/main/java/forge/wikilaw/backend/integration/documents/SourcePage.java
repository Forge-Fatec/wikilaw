package forge.wikilaw.backend.integration.documents;

import java.util.List;
import tools.jackson.databind.JsonNode;

public record SourcePage(List<JsonNode> registros, Long registroBrutoId, Continuacao proxima, List<String> avisos) {
    public SourcePage(List<JsonNode> registros, Long registroBrutoId, Continuacao proxima) {
        this(registros, registroBrutoId, proxima, List.of());
    }
    public record Continuacao(Integer pagina, Integer offset, String resumptionToken, String recursoId) {}
}
