package forge.wikilaw.backend.dto;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * Ponto de partida de uma pesquisa acompanhada: o maior id de cada tipo de documento quando ela passou a ser
 * acompanhada. Documento com id maior entrou no acervo depois e conta como novidade.
 */
public record MarcoAcompanhamento(
        @PositiveOrZero long decisoes,
        @PositiveOrZero long precedentes,
        @PositiveOrZero long doutrina) {

    public long idDe(String categoria) {
        return switch (categoria) {
            case "decisoes" -> decisoes;
            case "precedentes" -> precedentes;
            case "doutrina" -> doutrina;
            default -> throw new IllegalArgumentException("Categoria invalida: " + categoria);
        };
    }
}
