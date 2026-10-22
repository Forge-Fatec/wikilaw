package forge.wikilaw.backend.integration.datajud.dto;

import forge.wikilaw.backend.integration.datajud.DataJudTribunal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.AssertTrue;
import java.time.OffsetDateTime;

public record DataJudImportRequest(
        @NotNull DataJudTribunal tribunal,
        @Pattern(regexp = "^(?:[0-9]{20}|[0-9.\\-]{25})$",
                message = "deve conter um número CNJ com 20 dígitos, com ou sem formatação")
        String numeroProcesso,
        @Min(1) @Max(100) Integer tamanhoPagina,
        @Min(1) @Max(100000) Integer maximoPaginas,
        OffsetDateTime desde,
        OffsetDateTime ate) {

    public DataJudImportRequest(DataJudTribunal tribunal, String numeroProcesso,
            Integer tamanhoPagina, Integer maximoPaginas) {
        this(tribunal, numeroProcesso, tamanhoPagina, maximoPaginas, null, null);
    }

    @AssertTrue(message = "desde deve ser anterior ou igual a ate")
    public boolean isIntervaloValido() {
        return desde == null || ate == null || !desde.isAfter(ate);
    }

    public int tamanhoPaginaEfetivo() {
        return tamanhoPagina == null ? 10 : tamanhoPagina;
    }

    public int maximoPaginasEfetivo() {
        return maximoPaginas == null ? 1 : maximoPaginas;
    }
}
