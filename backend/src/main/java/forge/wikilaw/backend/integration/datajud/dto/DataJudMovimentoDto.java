package forge.wikilaw.backend.integration.datajud.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DataJudMovimentoDto(
        Long codigo,
        String nome,
        String dataHora,
        List<DataJudComplementoDto> complementosTabelados,
        DataJudOrgaoDto orgaoJulgador) {
}
