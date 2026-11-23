package forge.wikilaw.backend.service.pesquisa;

import forge.wikilaw.backend.dto.PesquisaAtivaRequest;
import forge.wikilaw.backend.dto.PesquisaPdfRequest.DocumentoReferencia;
import forge.wikilaw.backend.service.DocumentQueryService.Summary;
import java.util.List;

/**
 * Pesquisa que o usuario tem na tela, com os resultados recuperados do acervo na ordem exibida.
 *
 * @param termo termo digitado pelo usuario, ou {@code null}
 * @param filtros como a pesquisa foi feita e os filtros aplicados
 * @param resultados resultados enviados pelo navegador, ja lidos do acervo
 * @param totalResultados quantos resultados a pesquisa tem, contando os que o navegador nao enviou
 */
public record PesquisaAtiva(
        String termo,
        PesquisaAtivaRequest filtros,
        List<ResultadoPesquisa> resultados,
        int totalResultados) {

    public PesquisaAtiva {
        resultados = List.copyOf(resultados);
        totalResultados = Math.max(totalResultados, resultados.size());
    }

    /**
     * @param posicao posicao do resultado na lista exibida, a partir de 1; e o numero que a IA cita
     */
    public record ResultadoPesquisa(int posicao, DocumentoReferencia referencia, Summary documento) {}
}
