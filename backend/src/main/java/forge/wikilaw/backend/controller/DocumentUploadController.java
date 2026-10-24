package forge.wikilaw.backend.controller;

import forge.wikilaw.backend.dto.*;
import forge.wikilaw.backend.service.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/pesquisas")
public class DocumentUploadController {
    private final DocumentUploadService upload;
    private final SearchContextService context;

    public DocumentUploadController(DocumentUploadService upload, SearchContextService context) {
        this.upload = upload; this.context = context;
    }

    @PostMapping(value = "/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentoUploadResponse> extract(@RequestPart("arquivo") MultipartFile file) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(upload.extract(file));
    }

    @PostMapping("/contexto")
    public ResponseEntity<SearchContextService.PreparedContext> prepare(@Valid @RequestBody ContextoPesquisaRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(context.prepare(request.termo(), request.texto()));
    }
}
