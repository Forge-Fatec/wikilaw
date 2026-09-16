package forge.wikilaw.backend.integration.datajud;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.integration.datajud.dto.DataJudImportRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DataJudQueryFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DataJudQueryFactory factory = new DataJudQueryFactory(objectMapper);

    @Test
    void createsControlledProcessNumberQueryAndSearchAfterCursor() throws Exception {
        DataJudImportRequest request = new DataJudImportRequest(
                DataJudTribunal.TJSP, "4001703-74.2026.8.26.0360", 25, 2);

        String json = factory.create(request, List.of(objectMapper.readTree("1787321410520")));
        var root = objectMapper.readTree(json);

        assertThat(root.path("size").asInt()).isEqualTo(25);
        assertThat(root.at("/query/match/numeroProcesso").asText())
                .isEqualTo("40017037420268260360");
        assertThat(root.path("search_after").get(0).asLong()).isEqualTo(1787321410520L);
    }
}
