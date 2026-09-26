package forge.wikilaw.backend.integration.documents;

import java.time.LocalDate;
import lombok.Builder;

@Builder
public record NormalizedDocument(
    String identificador, String titulo, String tipo, String resumo,
    String autores, String relator, String orgaoJulgador, String numeroProcesso,
    LocalDate dataJulgamento, LocalDate dataPublicacao, String dataOriginal,
    String decisao, String inteiroTeor, String numeroTema, String situacao,
    String tese, String doi, String idioma, String periodico, String issn,
    String palavrasChave, String url, String metadados, java.util.List<RelatedProcess> processosRelacionados,
    String tribunal
) {
    public record RelatedProcess(String numeroRegistro, String descricao, String relator, String leadingCase, Long registroBrutoId) {}
}
