package forge.wikilaw.backend.integration.datajud.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DataJudHitDto(
        @JsonProperty("_id") String id,
        @JsonProperty("_source") DataJudSourceDto source,
        List<JsonNode> sort) {
}
