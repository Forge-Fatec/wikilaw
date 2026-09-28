package forge.wikilaw.backend.integration.datajud;

import forge.wikilaw.backend.exception.DataJudParseException;
import forge.wikilaw.backend.integration.datajud.dto.DataJudResponseDto;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class DataJudParser {

    private final ObjectMapper objectMapper;

    public DataJudParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DataJudResponseDto parse(String payload) {
        try {
            DataJudResponseDto response = objectMapper.readValue(payload, DataJudResponseDto.class);
            if (response.hits() == null || response.hits().hits() == null) {
                throw new DataJudParseException("Resposta do DataJud sem a estrutura hits.hits");
            }
            return response;
        } catch (JacksonException exception) {
            throw new DataJudParseException("Resposta JSON inválida recebida do DataJud", exception);
        }
    }
}
