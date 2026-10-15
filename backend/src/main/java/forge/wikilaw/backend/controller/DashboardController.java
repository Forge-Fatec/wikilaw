package forge.wikilaw.backend.controller;

import forge.wikilaw.backend.dto.DashboardResponse;
import forge.wikilaw.backend.service.DashboardService;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Recebe os mesmos filtros da pesquisa para que o dashboard reflita o recorte ativo. */
@Validated
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    public DashboardResponse resumir(
            @RequestParam(required = false) @Size(max = 300) String termo,
            @RequestParam(required = false) @Size(max = 30) String fonte,
            @RequestParam(required = false) @Size(max = 20) String tribunal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataDe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataAte) {
        return service.resumir(termo, fonte, tribunal, dataDe, dataAte);
    }
}
