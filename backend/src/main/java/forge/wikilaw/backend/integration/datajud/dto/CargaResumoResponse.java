package forge.wikilaw.backend.integration.datajud.dto;

import forge.wikilaw.backend.entity.CargaStatus;

public record CargaResumoResponse(
        Long idCarga,
        String fonte,
        String tribunal,
        CargaStatus status,
        int recebidos,
        int processados,
        int erros,
        String mensagemErro) {
}
