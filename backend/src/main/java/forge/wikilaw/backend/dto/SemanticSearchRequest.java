package forge.wikilaw.backend.dto;

import forge.wikilaw.backend.entity.DocumentoSemantico.Categoria;
import forge.wikilaw.backend.service.semantic.SemanticCaseQuery;
import forge.wikilaw.backend.service.semantic.SemanticSearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Caso pesquisado em partes (fatos, fundamentos, pedidos) e/ou descricao livre; ao menos uma precisa vir preenchida.
 * Sem categorias, busca nas tres; sem limite, devolve 10 documentos.
 */
public record SemanticSearchRequest(
        @Size(max = 20_000) String descricao,
        @Size(max = 20_000) String fatos,
        @Size(max = 20_000) String fundamentos,
        @Size(max = 20_000) String pedidos,
        @Size(max = 3) List<@Pattern(regexp = "decisoes|precedentes|doutrina") String> categorias,
        @Min(1) @Max(50) Integer limite) {

    public static final int LIMITE_PADRAO = 10;

    public SemanticCaseQuery toQuery() {
        return new SemanticCaseQuery(descricao, fatos, fundamentos, pedidos);
    }

    public List<Categoria> categoriasSelecionadas() {
        return categorias == null ? List.of()
                : categorias.stream().distinct().map(SemanticSearchService::fromApiCategory).toList();
    }

    public int limiteOuPadrao() {
        return limite == null ? LIMITE_PADRAO : limite;
    }
}
