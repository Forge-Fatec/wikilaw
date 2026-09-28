package forge.wikilaw.backend.integration.datajud.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DataJudSourceDto(
        String id,
        String tribunal,
        String grau,
        String numeroProcesso,
        String dataAjuizamento,
        Integer nivelSigilo,
        DataJudOrgaoDto orgaoJulgador,
        DataJudCodeNameDto classe,
        DataJudCodeNameDto sistema,
        DataJudCodeNameDto formato,
        String dataHoraUltimaAtualizacao,
        @JsonProperty("@timestamp") String timestamp,
        List<DataJudMovimentoDto> movimentos,
        @JsonDeserialize(using = DataJudAssuntosDeserializer.class)
        List<DataJudCodeNameDto> assuntos) {
}
