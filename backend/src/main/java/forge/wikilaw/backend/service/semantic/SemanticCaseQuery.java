package forge.wikilaw.backend.service.semantic;

/**
 * Caso pesquisado em partes: o que aconteceu (fatos), a base legal invocada (fundamentos) e o que se pede
 * (pedidos). A descricao livre continua aceita para quem nao separa o caso nessas partes.
 */
public record SemanticCaseQuery(String descricao, String fatos, String fundamentos, String pedidos) {

    public static SemanticCaseQuery livre(String descricao) {
        return new SemanticCaseQuery(descricao, null, null, null);
    }
}
