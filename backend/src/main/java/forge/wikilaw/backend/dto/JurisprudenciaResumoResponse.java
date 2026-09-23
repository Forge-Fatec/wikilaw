package forge.wikilaw.backend.dto;

import java.time.LocalDate;

public record JurisprudenciaResumoResponse(
        Long id,
        String fonte,
        String tribunal,
        String identificadorExterno,
        String numeroProcesso,
        String tipoDecisao,
        String titulo,
        String ementa,
        String decisao,
        String relator,
        String orgaoJulgador,
        boolean possuiInteiroTeor,
        LocalDate dataJulgamento,
        LocalDate dataPublicacao,
        String dataOriginal,
        String urlOriginal) {
}
