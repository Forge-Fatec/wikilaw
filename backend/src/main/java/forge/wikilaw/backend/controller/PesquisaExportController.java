package forge.wikilaw.backend.controller;

import forge.wikilaw.backend.dto.PesquisaPdfRequest;
import forge.wikilaw.backend.service.PesquisaPdfService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pesquisas/exportacoes")
public class PesquisaExportController {
    private final PesquisaPdfService service;

    public PesquisaExportController(PesquisaPdfService service) { this.service = service; }

    @PostMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@Valid @RequestBody PesquisaPdfRequest request) {
        byte[] pdf = service.generate(request);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("wikilaw-pesquisa-" + LocalDate.now(ZoneId.of("America/Sao_Paulo")) + ".pdf").build().toString())
                .cacheControl(CacheControl.noStore())
                .contentLength(pdf.length)
                .body(pdf);
    }
}
