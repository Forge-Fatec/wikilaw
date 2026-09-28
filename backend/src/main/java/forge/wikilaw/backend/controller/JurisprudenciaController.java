package forge.wikilaw.backend.controller;

import forge.wikilaw.backend.dto.JurisprudenciaSearchResponse;
import forge.wikilaw.backend.service.JurisprudenciaQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/jurisprudencias")
public class JurisprudenciaController {

    private final JurisprudenciaQueryService service;

    public JurisprudenciaController(JurisprudenciaQueryService service) {
        this.service = service;
    }

    @GetMapping
    public JurisprudenciaSearchResponse buscar(
            @RequestParam(required = false) @Size(max = 300) String termo,
            @RequestParam(required = false) @Size(max = 30) String fonte,
            @RequestParam(required = false) @Size(max = 20) String tribunal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataDe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataAte,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return service.buscar(termo, fonte, tribunal, dataDe, dataAte, pagina, tamanho);
    }
}
