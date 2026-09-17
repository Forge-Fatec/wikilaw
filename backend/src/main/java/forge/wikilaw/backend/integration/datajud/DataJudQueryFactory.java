package forge.wikilaw.backend.integration.datajud;

import forge.wikilaw.backend.integration.datajud.dto.DataJudImportRequest;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class DataJudQueryFactory {

    private final ObjectMapper objectMapper;

    public DataJudQueryFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String create(DataJudImportRequest request, List<JsonNode> searchAfter) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("size", request.tamanhoPaginaEfetivo());

        ObjectNode query = root.putObject("query");
        if (request.numeroProcesso() == null || request.numeroProcesso().isBlank()) {
            query.putObject("match_all");
        } else {
            query.putObject("match")
                    .put("numeroProcesso", request.numeroProcesso().replaceAll("\\D", ""));
        }

        root.putArray("sort")
                .addObject()
                .putObject("@timestamp")
                .put("order", "asc");

        if (searchAfter != null && !searchAfter.isEmpty()) {
            ArrayNode cursor = root.putArray("search_after");
            searchAfter.forEach(cursor::add);
        }

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Não foi possível montar a consulta DataJud", exception);
        }
    }
}
