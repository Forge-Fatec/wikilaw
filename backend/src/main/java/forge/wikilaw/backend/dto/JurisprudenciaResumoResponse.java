package forge.wikilaw.backend.dto;

import java.time.LocalDate;

public record JurisprudenciaResumoResponse(
        Long id,
        String fonte,
        String tribunal,
        String numeroProcesso,
        String titulo,
        String ementa,
        String relator,
        String orgaoJulgador,
        LocalDate dataJulgamento,
        LocalDate dataPublicacao,
        String urlOriginal) {
}
