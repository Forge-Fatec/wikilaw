package forge.wikilaw.backend.integration.datajud;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.integration.NormalizedProcessRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DataJudMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DataJudParser parser = new DataJudParser(objectMapper);
    private final DataJudMapper mapper = new DataJudMapper(objectMapper);

    @Test
    void normalizesProcessClassSubjectsAndMovements() throws IOException {
        var hit = parser.parse(fixture()).hits().hits().get(0);

        NormalizedProcessRecord record = mapper.map(hit, DataJudTribunal.TJSP);

        assertThat(record.numeroCnj()).isEqualTo("40017037420268260360");
        assertThat(record.dataAjuizamento()).isEqualTo(OffsetDateTime.parse("2026-06-15T15:48:38Z"));
        assertThat(record.classe().codigo()).isEqualTo(12154L);
        assertThat(record.classe().nome()).isEqualTo("Execução de Título Extrajudicial");
        assertThat(record.assuntos()).extracting(NormalizedProcessRecord.NormalizedClassificacao::codigo)
                .containsExactly(4972L);
        assertThat(record.movimentos()).hasSize(2);
        assertThat(record.movimentos().get(0).complementosJson()).contains("tipo_de_documento");
        assertThat(record.movimentos().get(1).nome()).isNull();
    }

    @Test
    void flattensNestedSubjectsFoundInPublishedDataJudExamples() {
        String payload = """
                {"hits":{"total":{"value":1,"relation":"eq"},"hits":[{
                  "_id":"TJSP_G1_1_00000000000000000000",
                  "_source":{"id":"TJSP_G1_1_00000000000000000000","tribunal":"TJSP",
                  "numeroProcesso":"00000000000000000000","assuntos":[[{"codigo":10,"nome":"A"}],[{"codigo":10,"nome":"A"},{"codigo":20,"nome":"B"}]]}
                }]}}
                """;

        NormalizedProcessRecord record = mapper.map(
                parser.parse(payload).hits().hits().get(0), DataJudTribunal.TJSP);

        assertThat(record.assuntos()).extracting(NormalizedProcessRecord.NormalizedClassificacao::codigo)
                .containsExactly(10L, 20L);
    }

    private String fixture() throws IOException {
        try (var stream = getClass().getResourceAsStream("/fixtures/datajud-response.json")) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
