package forge.wikilaw.backend.dto;

import java.util.List;

public record PrecedenteSearchResponse(
        List<PrecedenteResumoResponse> itens,
        int pagina,
        int tamanho,
        long total,
        int totalPaginas) {
}
