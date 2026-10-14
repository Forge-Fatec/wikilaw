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
        @NotNull @Pattern(regexp = "mais-recentes|mais-antigos") String ordenacao,
        @NotEmpty @Size(max = 5000) List<@NotNull @Valid DocumentoReferencia> resultados) {

    public record DocumentoReferencia(
            @NotNull @Pattern(regexp = "decisoes|precedentes|doutrina") String categoria,
            @NotNull @Positive Long id) {}
}
