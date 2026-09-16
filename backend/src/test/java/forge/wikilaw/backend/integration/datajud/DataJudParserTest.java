package forge.wikilaw.backend.integration.datajud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import forge.wikilaw.backend.exception.DataJudParseException;
import forge.wikilaw.backend.integration.datajud.dto.DataJudResponseDto;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DataJudParserTest {

    private final DataJudParser parser = new DataJudParser(new ObjectMapper());

    @Test
    void parsesRealDataJudResponseShape() throws IOException {
        String payload = fixture();

        DataJudResponseDto response = parser.parse(payload);

        assertThat(response.hits().hits()).hasSize(1);
        assertThat(response.hits().hits().get(0).source().numeroProcesso())
                .isEqualTo("40017037420268260360");
        assertThat(response.hits().hits().get(0).source().movimentos()).hasSize(2);
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> parser.parse("{not-json"))
                .isInstanceOf(DataJudParseException.class)
                .hasMessageContaining("JSON inválida");
    }

    @Test
    void rejectsJsonWithoutHits() {
        assertThatThrownBy(() -> parser.parse("{\"took\":1}"))
                .isInstanceOf(DataJudParseException.class)
                .hasMessageContaining("hits.hits");
    }

    private String fixture() throws IOException {
        try (var stream = getClass().getResourceAsStream("/fixtures/datajud-response.json")) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
