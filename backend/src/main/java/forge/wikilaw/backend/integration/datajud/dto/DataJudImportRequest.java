package forge.wikilaw.backend.integration.datajud.dto;

import forge.wikilaw.backend.integration.datajud.DataJudTribunal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record DataJudImportRequest(
        @NotNull DataJudTribunal tribunal,
        @Pattern(regexp = "^(?:[0-9]{20}|[0-9.\\-]{25})$",
                message = "deve conter um número CNJ com 20 dígitos, com ou sem formatação")
        String numeroProcesso,
        @Min(1) @Max(100) Integer tamanhoPagina,
        @Min(1) @Max(50) Integer maximoPaginas) {

    public int tamanhoPaginaEfetivo() {
        return tamanhoPagina == null ? 10 : tamanhoPagina;
    }

    public int maximoPaginasEfetivo() {
        return maximoPaginas == null ? 1 : maximoPaginas;
    }
}
