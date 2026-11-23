package forge.wikilaw.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Pesquisa exibida ao usuario no momento em que ele pede a analise: como foi feita e quais filtros
 * estao aplicados. Os resultados em si chegam como referencias em {@link AnaliseJuridicaRequest#resultados()}.
 *
 * @param modo {@code textual} (termo no acervo) ou {@code similaridade} (busca semantica)
 * @param consultaContextual consulta montada a partir do documento anexado, quando houver
 * @param documentoContexto nome do documento anexado como contexto do caso
 * @param categorias categorias marcadas ({@code decisoes}, {@code precedentes}, {@code doutrina}); vazia = todas
 * @param fontes siglas das fontes pesquisadas; vazia = todas
 * @param resultadoDecisao recorte do grafico de resultado das decisoes (ex.: "Provido")
 * @param orgaoJulgador recorte do grafico de orgao julgador
 * @param totalResultados quantos resultados a pesquisa ativa tem, mesmo que nem todos sejam enviados
 */
public record PesquisaAtivaRequest(
        @Pattern(regexp = "textual|similaridade") String modo,
        @Size(max = 2000) String consultaContextual,
        @Size(max = 180) String documentoContexto,
        @Size(max = 3) List<@Pattern(regexp = "decisoes|precedentes|doutrina") String> categorias,
        @Size(max = 20) List<@Size(max = 30) String> fontes,
        @Size(max = 100) String tribunal,
        LocalDate dataDe,
        LocalDate dataAte,
        @Pattern(regexp = "mais-recentes|mais-antigos|relevancia") String ordenacao,
        @Size(max = 100) String resultadoDecisao,
        @Size(max = 200) String orgaoJulgador,
        @Min(0) Integer totalResultados) {

    public PesquisaAtivaRequest {
        categorias = categorias == null ? List.of() : List.copyOf(categorias);
        fontes = fontes == null ? List.of() : List.copyOf(fontes);
    }

    /** Pesquisa sem descricao: requisicoes antigas, que enviam so o termo e as referencias. */
    public static PesquisaAtivaRequest vazia() {
        return new PesquisaAtivaRequest(null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /** Os resultados vieram da busca semantica ou estao ordenados por relevancia. */
    public boolean ordenadaPorRelevancia() {
        return "similaridade".equals(modo) || "relevancia".equals(ordenacao);
    }
}
