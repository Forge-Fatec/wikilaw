package forge.wikilaw.backend.integration.datajud.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DataJudComplementoDto(Long codigo, String descricao, JsonNode valor, String nome) {
}
