package forge.wikilaw.backend.dto;

/**
 * Processo vinculado a um precedente, para exibição.
 *
 * <p>Substitui a serialização direta da entidade, que expunha as chaves
 * internas {@code id}, {@code idPrecedente} e {@code idRegistroBruto}.
 *
 * <p>O CSV do STJ traz o leading case como "S"/"N"; aqui vira booleano para a
 * tela não precisar interpretar o código da fonte. Valor desconhecido fica nulo.
 */
public record PrecedenteProcessoResponse(
        String numeroRegistro,
        String descricao,
        String relator,
        Boolean leadingCase) {
}
