package forge.wikilaw.backend.integration.datajud.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DataJudOrgaoDto(
        @JsonAlias("codigoOrgao") String codigo,
        @JsonAlias("nomeOrgao") String nome,
        Long codigoMunicipioIBGE) {
}
