package forge.wikilaw.backend.service.pesquisa;

import forge.wikilaw.backend.service.pesquisa.PesquisaAtiva.ResultadoPesquisa;
import java.util.List;

/**
 * Resultados da pesquisa ativa escolhidos para a analise e os que ficaram de fora, ambos na ordem da pesquisa.
 */
public record SelecaoResultados(List<ResultadoPesquisa> selecionados, List<ResultadoPesquisa> omitidos) {

    public SelecaoResultados {
        selecionados = List.copyOf(selecionados);
        omitidos = List.copyOf(omitidos);
    }
}
