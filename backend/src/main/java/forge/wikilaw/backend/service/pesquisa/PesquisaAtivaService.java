package forge.wikilaw.backend.service.pesquisa;

import forge.wikilaw.backend.dto.AnaliseJuridicaRequest;
import forge.wikilaw.backend.dto.PesquisaAtivaRequest;
import forge.wikilaw.backend.dto.PesquisaPdfRequest.DocumentoReferencia;
import forge.wikilaw.backend.exception.ContextoInsuficienteException;
import forge.wikilaw.backend.service.DocumentQueryService;
import forge.wikilaw.backend.service.DocumentQueryService.Summary;
import forge.wikilaw.backend.service.pesquisa.PesquisaAtiva.ResultadoPesquisa;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Recupera do acervo os resultados da pesquisa ativa do usuario.
 * O navegador envia so as referencias; o texto vem do banco, entao nao da para enviar outro conteudo como acervo.
 */
@Service
public class PesquisaAtivaService {

    private final DocumentQueryService documents;

    public PesquisaAtivaService(DocumentQueryService documents) {
        this.documents = documents;
    }

    /**
     * @throws ContextoInsuficienteException se a pesquisa nao tem resultados
     * @throws ResponseStatusException 400 para resultados repetidos ou fora das categorias pesquisadas,
     *         409 se algum resultado saiu do acervo
     */
    public PesquisaAtiva recuperar(AnaliseJuridicaRequest request) {
        List<DocumentoReferencia> referencias = request.resultados();
        if (referencias == null || referencias.isEmpty()) throw new ContextoInsuficienteException();
        validar(request.pesquisa(), referencias);

        Map<DocumentoReferencia, Summary> encontrados = carregar(referencias);
        if (encontrados.size() != referencias.size())
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Um resultado não está mais disponível. Atualize a pesquisa e tente novamente.");

        var resultados = new ArrayList<ResultadoPesquisa>(referencias.size());
        for (int i = 0; i < referencias.size(); i++)
            resultados.add(new ResultadoPesquisa(i + 1, referencias.get(i), encontrados.get(referencias.get(i))));
        Integer total = request.pesquisa().totalResultados();
        return new PesquisaAtiva(request.termo(), request.pesquisa(), resultados, total == null ? 0 : total);
    }

    private static void validar(PesquisaAtivaRequest pesquisa, List<DocumentoReferencia> referencias) {
        if (new LinkedHashSet<>(referencias).size() != referencias.size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resultados repetidos na solicitação");
        if (!pesquisa.categorias().isEmpty()
                && referencias.stream().anyMatch(ref -> !pesquisa.categorias().contains(ref.categoria())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Um resultado não pertence às categorias da pesquisa ativa");
    }

    /** Uma consulta por categoria; documentos inativos nao voltam. */
    private Map<DocumentoReferencia, Summary> carregar(List<DocumentoReferencia> referencias) {
        Map<String, List<Long>> idsPorCategoria = new LinkedHashMap<>();
        referencias.forEach(ref -> idsPorCategoria.computeIfAbsent(ref.categoria(), key -> new ArrayList<>()).add(ref.id()));
        Map<DocumentoReferencia, Summary> encontrados = new HashMap<>();
        idsPorCategoria.forEach((categoria, ids) -> documents.summariesByIds(categoria, ids)
                .forEach(row -> encontrados.put(new DocumentoReferencia(categoria, row.id()), row)));
        return encontrados;
    }
}
