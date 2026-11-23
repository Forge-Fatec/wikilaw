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
 * {@code fontes} lista so os documentos que a analise realmente cita, com as secoes que cada um sustenta.
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
        boolean contextoResumido,
        List<FonteAnalise> fontes) {

    /** @param numero posicao do documento na pesquisa, o mesmo numero das citacoes [n] */
    public record DocumentoRelevante(int numero, String categoria, Long id, String titulo, String motivo) {}

    public record ResultadoUtilizado(int posicao, String categoria, Long id) {}

    /**
     * Fonte que fundamenta a analise.
     *
     * @param numero posicao do documento na pesquisa, o mesmo numero das citacoes [n]
     * @param fonte sigla da fonte de dados
     * @param identificadorExterno identificador do documento na fonte
     * @param url endereco do documento original (so http/https), ou {@code null} se a fonte nao informou
     * @param secoes secoes da analise que citam esta fonte, na ordem em que aparecem
     */
    public record FonteAnalise(
            int numero,
            String categoria,
            Long id,
            String titulo,
            String fonte,
            String tribunal,
            String numeroProcessoOuTema,
            String identificadorExterno,
            String url,
            List<String> secoes) {

        public FonteAnalise {
            secoes = List.copyOf(secoes);
        }
    }
}
