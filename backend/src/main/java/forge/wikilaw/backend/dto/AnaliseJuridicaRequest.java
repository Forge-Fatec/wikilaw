package forge.wikilaw.backend.dto;

import forge.wikilaw.backend.dto.PesquisaPdfRequest.DocumentoReferencia;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Resultados de uma pesquisa ja feita que a IA deve analisar, na ordem exibida ao usuario.
 * O texto dos documentos e lido do acervo; o navegador envia apenas as referencias.
 */
public record AnaliseJuridicaRequest(
        @Size(max = 2000) String termo,
        @NotEmpty @Size(max = AnaliseJuridicaRequest.MAX_DOCUMENTOS)
        List<@NotNull @Valid DocumentoReferencia> resultados) {

    public static final int MAX_DOCUMENTOS = 10;
}
