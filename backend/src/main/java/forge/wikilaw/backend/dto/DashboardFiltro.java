package forge.wikilaw.backend.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Mesmos filtros da busca de documentos, para o dashboard refletir o recorte ativo.
 * fonte pode se repetir na query string (uma por fonte marcada); vazia = todas.
 * resultado e orgaoJulgador são as categorias selecionadas nos gráficos
 * (rótulo exibido na barra).
 */
public record DashboardFiltro(
        @Size(max = 300) String termo,
        @Size(max = 20) List<@Size(max = 30) String> fonte,
        @Size(max = 20) String tribunal,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataDe,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataAte,
        @Size(max = 40) String resultado,
        @Size(max = 200) String orgaoJulgador) {

    public static DashboardFiltro semFiltros() {
        return new DashboardFiltro(null, null, null, null, null, null, null);
    }
}
