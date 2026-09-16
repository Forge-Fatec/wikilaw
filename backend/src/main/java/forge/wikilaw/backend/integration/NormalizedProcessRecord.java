package forge.wikilaw.backend.integration;

import java.time.OffsetDateTime;
import java.util.List;

public record NormalizedProcessRecord(
        String identificadorExterno,
        String tribunalSigla,
        String numeroCnj,
        String grau,
        OffsetDateTime dataAjuizamento,
        Integer nivelSigilo,
        String codigoSistema,
        String nomeSistema,
        String formatoProcesso,
        OffsetDateTime dataUltimaAtualizacaoFonte,
        NormalizedOrgao orgaoJulgadorAtual,
        NormalizedClassificacao classe,
        List<NormalizedClassificacao> assuntos,
        List<NormalizedMovimento> movimentos) {

    public record NormalizedOrgao(String codigo, String nome, Long codigoMunicipioIbge) {
    }

    public record NormalizedClassificacao(Long codigo, String nome) {
    }

    public record NormalizedMovimento(
            Long codigo,
            String nome,
            OffsetDateTime dataHora,
            String complementosJson,
            NormalizedOrgao orgaoJulgador) {
    }
}
