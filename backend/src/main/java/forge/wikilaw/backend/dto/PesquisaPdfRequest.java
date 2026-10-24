package forge.wikilaw.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

/** Ordered references preserve the result set selected in the browser, across all pages. */
public record PesquisaPdfRequest(
        @Size(max = 300) String termo,
        @NotEmpty @Size(max = 3) List<@Pattern(regexp = "decisoes|precedentes|doutrina") String> categorias,
        @Size(max = 100) String fonte,
        @Size(max = 100) String tribunal,
        LocalDate dataDe,
        LocalDate dataAte,
        @NotNull @Pattern(regexp = "mais-recentes|mais-antigos|mais-similares")String ordenacao,
        @NotEmpty @Size(max = 5000) List<@NotNull @Valid DocumentoReferencia> resultados,
        @Valid IntervaloPaginas intervaloPaginas) {

    /** Compatibility with exports created before page selection was introduced. */
    public PesquisaPdfRequest(String termo, List<String> categorias, String fonte, String tribunal,
            LocalDate dataDe, LocalDate dataAte, String ordenacao, List<DocumentoReferencia> resultados) {
        this(termo, categorias, fonte, tribunal, dataDe, dataAte, ordenacao, resultados, null);
    }

    public record IntervaloPaginas(
            @NotNull @Min(1) Integer de,
            @NotNull @Min(1) Integer ate,
            @NotNull @Min(1) Integer totalResultados,
            @NotNull @Min(1) @Max(100) Integer itensPorPagina) {}

    public record DocumentoReferencia(
            @NotNull @Pattern(regexp = "decisoes|precedentes|doutrina") String categoria,
            @NotNull @Positive Long id) {}
}
