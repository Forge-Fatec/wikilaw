package forge.wikilaw.backend.dto;

import java.time.LocalDate;

/**
 * Detalhe de um documento para exibição.
 *
 * <p>Substitui a serialização direta da entidade JPA, que expunha campos
 * internos (ativo, idFonte, idRegistroBruto, atualizadoEm e o payload bruto em
 * metadados) e devolvia fonte e tribunal apenas como id numérico, sem a sigla
 * que a tela precisa mostrar.
 *
 * <p>Ao contrário do resumo da listagem, aqui cada categoria tem campos com
 * nome próprio — {@code numeroProcesso} e {@code numeroTema} em vez de um
 * campo "ou" — porque a tela de detalhe já sabe que categoria está exibindo.
 * Campos de outra categoria chegam nulos.
 */
public record DocumentoDetalheResponse(
        Long id,
        String categoria,
        String fonte,
        String identificadorExterno,
        String titulo,
        String tipoDocumento,
        String tribunal,
        String orgaoJulgador,
        String numeroProcesso,
        String numeroTema,
        String relator,
        String autores,
        String ementa,
        String questaoJuridica,
        String resumo,
        String decisao,
        String tese,
        String situacao,
        String inteiroTeor,
        Boolean possuiInteiroTeor,
        String periodico,
        String doi,
        String issn,
        String idioma,
        String palavrasChave,
        LocalDate dataJulgamento,
        LocalDate dataPublicacao,
        String dataOriginal,
        String urlOriginal) {
}
