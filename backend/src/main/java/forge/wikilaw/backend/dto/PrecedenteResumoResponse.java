package forge.wikilaw.backend.dto;

import java.time.LocalDate;

/**
 * Item de resultado da busca de precedentes.
 *
 * <p>Diferente da jurisprudência, o que responde à pesquisa aqui é a
 * {@code questaoJuridica} (o que foi submetido a julgamento) e a {@code tese}
 * (o que ficou firmado), por isso ambas são expostas separadamente.
 */
public record PrecedenteResumoResponse(
        Long id,
        String fonte,
        String tribunal,
        String identificadorExterno,
        String numeroTema,
        String tipoPrecedente,
        String titulo,
        String questaoJuridica,
        String tese,
        String situacao,
        LocalDate dataJulgamento,
        LocalDate dataPublicacao,
        String dataOriginal,
        String urlOriginal) {
}
