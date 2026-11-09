package forge.wikilaw.backend.dto;

import forge.wikilaw.backend.dto.PesquisaPdfRequest.DocumentoReferencia;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Analise juridica em secoes fixas, para a tela exibir cada parte com destaque proprio.
 * {@code geradaPorIa}, {@code aviso} e {@code modelo} identificam o conteudo como produzido por IA.
 * {@code resultadosUtilizados} lista os resultados enviados como contexto com a posicao de cada um na pesquisa,
 * que e o numero das citacoes [n];
 * {@code resultadosOmitidos}, os que ficaram de fora pelo limite de contexto do modelo.
 */
public record AnaliseJuridicaResponse(
        String resumo,
        String questaoJuridica,
        String entendimentoPredominante,
        List<String> pontosPrincipais,
        List<String> fundamentosLegais,
        List<String> divergencias,
        List<DocumentoRelevante> documentosRelevantes,
        List<String> limitacoes,
        boolean geradaPorIa,
        String aviso,
        String modelo,
        OffsetDateTime geradaEm,
        int documentosAnalisados,
        List<ResultadoUtilizado> resultadosUtilizados,
        List<DocumentoReferencia> resultadosOmitidos,
        boolean contextoResumido) {

    public record DocumentoRelevante(String categoria, Long id, String titulo, String motivo) {}

    public record ResultadoUtilizado(int posicao, String categoria, Long id) {}
}
