package forge.wikilaw.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Resposta da IA a uma pergunta sobre a analise. {@code historicoResumido} indica que so as trocas mais recentes
 * da conversa foram enviadas ao modelo.
 */
public record RespostaAnaliseResponse(
        String resposta,
        List<DocumentoCitado> documentosCitados,
        String aviso,
        String modelo,
        OffsetDateTime geradaEm,
        boolean historicoResumido) {

    /** Documento citado na resposta pelo numero, ligado ao resultado da pesquisa. */
    public record DocumentoCitado(int posicao, String categoria, Long id, String titulo) {}
}
