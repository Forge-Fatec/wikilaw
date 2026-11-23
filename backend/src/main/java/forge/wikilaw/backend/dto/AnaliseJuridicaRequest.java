package forge.wikilaw.backend.dto;

import forge.wikilaw.backend.dto.PesquisaPdfRequest.DocumentoReferencia;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Pesquisa ativa que a IA deve analisar: o termo, a descricao da pesquisa e as referencias dos resultados,
 * na ordem exibida ao usuario. O texto dos documentos e lido do acervo; o navegador envia apenas as referencias.
 * Uma lista vazia e respondida com a falta de resultados para contextualizar a analise, e uma lista maior
 * que o limite do modelo e reduzida aos resultados mais relevantes ({@code wikilaw.ai.contexto.*}).
 */
public record AnaliseJuridicaRequest(
        @Size(max = 2000) String termo,
        @Valid PesquisaAtivaRequest pesquisa,
        @Size(max = AnaliseJuridicaRequest.MAX_RESULTADOS)
        List<@NotNull @Valid DocumentoReferencia> resultados) {

    /** Teto de referencias por requisicao; quantas chegam a IA depende do limite de contexto configurado. */
    public static final int MAX_RESULTADOS = 200;

    public AnaliseJuridicaRequest {
        if (pesquisa == null) pesquisa = PesquisaAtivaRequest.vazia();
    }

    /** Requisicao sem a descricao da pesquisa. */
    public AnaliseJuridicaRequest(String termo, List<DocumentoReferencia> resultados) {
        this(termo, null, resultados);
    }
}
