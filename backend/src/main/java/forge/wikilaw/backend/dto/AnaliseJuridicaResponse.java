package forge.wikilaw.backend.dto;

import forge.wikilaw.backend.dto.PesquisaPdfRequest.DocumentoReferencia;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Analise juridica em secoes fixas, para a tela exibir cada parte com destaque proprio.
 * {@code geradaPorIa}, {@code aviso} e {@code modelo} identificam o conteudo como produzido por IA.
 * {@code resultadosUtilizados} lista, na ordem das citacoes [1], [2]..., os resultados enviados como contexto;
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
        List<DocumentoReferencia> resultadosUtilizados,
        List<DocumentoReferencia> resultadosOmitidos,
        boolean contextoResumido) {

    public record DocumentoRelevante(String categoria, Long id, String titulo, String motivo) {}
}
