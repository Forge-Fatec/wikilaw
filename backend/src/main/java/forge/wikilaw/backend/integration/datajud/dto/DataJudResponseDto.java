package forge.wikilaw.backend.integration.datajud.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DataJudResponseDto(Hits hits) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hits(Total total, List<DataJudHitDto> hits) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Total(long value, String relation) {
    }
}
