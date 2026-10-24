package forge.wikilaw.backend.controller;

import forge.wikilaw.backend.dto.SimilaridadeRequest;
import forge.wikilaw.backend.service.semantic.VectorSearchService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pesquisas/similaridade")
public class SimilaridadeController {
    private final VectorSearchService search;
    public SimilaridadeController(VectorSearchService search) { this.search=search; }
    @GetMapping("/status")
    public ResponseEntity<VectorSearchService.Status> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(search.status());
    }
    @PostMapping
    public ResponseEntity<VectorSearchService.Results> search(@Valid @RequestBody SimilaridadeRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(search.search(request));
    }
}
