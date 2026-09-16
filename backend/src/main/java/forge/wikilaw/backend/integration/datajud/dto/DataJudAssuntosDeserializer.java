package forge.wikilaw.backend.integration.datajud.dto;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

public class DataJudAssuntosDeserializer extends StdDeserializer<List<DataJudCodeNameDto>> {

    public DataJudAssuntosDeserializer() {
        super(List.class);
    }

    @Override
    public List<DataJudCodeNameDto> deserialize(JsonParser parser, DeserializationContext context)
            throws JacksonException {
        JsonNode root = parser.readValueAsTree();
        List<DataJudCodeNameDto> assuntos = new ArrayList<>();
        collect(root, assuntos);
        return assuntos;
    }

    private void collect(JsonNode node, List<DataJudCodeNameDto> assuntos) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collect(child, assuntos));
            return;
        }
        if (node.isObject() && node.hasNonNull("codigo")) {
            Long codigo = node.get("codigo").canConvertToLong() ? node.get("codigo").longValue() : null;
            String nome = node.hasNonNull("nome") ? node.get("nome").asText() : null;
            assuntos.add(new DataJudCodeNameDto(codigo, nome));
        }
    }
}
