package forge.wikilaw.backend.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Analise juridica em secoes fixas, para a tela exibir cada parte com destaque proprio.
 * {@code geradaPorIa}, {@code aviso} e {@code modelo} identificam o conteudo como produzido por IA.
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
        int documentosAnalisados) {

    public record DocumentoRelevante(String categoria, Long id, String titulo, String motivo) {}
}
