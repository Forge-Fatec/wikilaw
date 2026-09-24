package forge.wikilaw.backend.controller;

import forge.wikilaw.backend.dto.DocumentoDetalheResponse;
import forge.wikilaw.backend.dto.PrecedenteProcessoResponse;
import forge.wikilaw.backend.service.DocumentQueryService;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/documentos")
public class DocumentQueryController {
    private final DocumentQueryService service;
    public DocumentQueryController(DocumentQueryService service) { this.service=service; }
    @GetMapping("/{categoria}")
    public DocumentQueryService.Result listar(@PathVariable String categoria,
            @RequestParam(required=false) String fonte,@RequestParam(required=false) @Size(max=300) String termo,
            @RequestParam(required=false) @Size(max=20) String tribunal,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate dataDe,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate dataAte,
            @RequestParam(defaultValue="0") @Min(0) @Max(100000) int pagina,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int tamanho) {
        return service.list(categoria,fonte,termo,tribunal,dataDe,dataAte,pagina,tamanho);
    }
    @GetMapping("/{categoria}/{id}")
    public DocumentoDetalheResponse detalhe(@PathVariable String categoria,@PathVariable Long id) {
        return service.detail(categoria,id);
    }
    @GetMapping("/precedentes/{id}/processos")
    public List<PrecedenteProcessoResponse> processos(@PathVariable Long id) { return service.related(id); }
}
