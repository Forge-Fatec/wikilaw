package forge.wikilaw.backend.dto;

import forge.wikilaw.backend.entity.DocumentoSemantico.Categoria;
import forge.wikilaw.backend.service.semantic.SemanticCaseQuery;
import forge.wikilaw.backend.service.semantic.SemanticSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Pesquisa acompanhada, guardada no navegador do usuario: o caso (como na busca semantica), os tipos de documento
 * e o marco a partir do qual um resultado conta como novo. Nada disso e salvo no banco.
 */
public record NovidadesAcompanhamentoRequest(
        @Size(max = 20_000) String descricao,
        @Size(max = 20_000) String fatos,
        @Size(max = 3) List<@Pattern(regexp = "decisoes|precedentes|doutrina") String> categorias,
        @NotNull @Valid MarcoAcompanhamento marco) {

    public SemanticCaseQuery toQuery() {
        return new SemanticCaseQuery(descricao, fatos, null, null);
    }

    public List<Categoria> categoriasSelecionadas() {
        return categorias == null ? List.of()
                : categorias.stream().distinct().map(SemanticSearchService::fromApiCategory).toList();
    }
}
