package forge.wikilaw.backend.controller;

import forge.wikilaw.backend.dto.DashboardFiltro;
import forge.wikilaw.backend.dto.DashboardResultadosRequest;
import forge.wikilaw.backend.dto.DashboardResponse;
import forge.wikilaw.backend.dto.DashboardResponse.DistribuicaoItem;
import forge.wikilaw.backend.dto.DashboardResponse.Indicadores;
import forge.wikilaw.backend.service.DashboardService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Todos os endpoints recebem os filtros da pesquisa (termo, fonte, tribunal,
 * dataDe, dataAte). O resumo devolve tudo numa chamada; os demais servem a
 * quem precisa de uma agregação só.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    public DashboardResponse resumir(@Valid DashboardFiltro filtro) {
        return service.resumir(filtro);
    }

    @PostMapping("/resultados")
    public DashboardResponse resultados(@Valid @RequestBody DashboardResultadosRequest request) {
        return service.resumirIds(request.idsDecisoes(),request.resultado(),request.orgaoJulgador());
    }

    @GetMapping("/indicadores")
    public Indicadores indicadores(@Valid DashboardFiltro filtro) {
        return service.indicadores(filtro);
    }

    @GetMapping("/distribuicao-merito")
    public List<DistribuicaoItem> distribuicaoMerito(@Valid DashboardFiltro filtro) {
        return service.distribuicaoMerito(filtro);
    }

    @GetMapping("/distribuicao-camara-turma")
    public List<DistribuicaoItem> distribuicaoCamaraTurma(@Valid DashboardFiltro filtro) {
        return service.distribuicaoCamaraTurma(filtro);
    }

    @GetMapping("/distribuicao-tribunal")
    public List<DistribuicaoItem> distribuicaoTribunal(@Valid DashboardFiltro filtro) {
        return service.distribuicaoTribunal(filtro);
    }

    @GetMapping("/evolucao-anual")
    public List<DistribuicaoItem> evolucaoAnual(@Valid DashboardFiltro filtro) {
        return service.evolucaoAnual(filtro);
    }
}
