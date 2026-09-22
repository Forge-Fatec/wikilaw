package forge.wikilaw.backend.dto;

import java.util.List;

public record JurisprudenciaSearchResponse(
        List<JurisprudenciaResumoResponse> itens,
        int pagina,
        int tamanho,
        long total,
        int totalPaginas) {
}
