package forge.wikilaw.backend.service;

import forge.wikilaw.backend.dto.PesquisaPdfRequest;
import forge.wikilaw.backend.dto.PesquisaPdfRequest.DocumentoReferencia;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PesquisaPdfService {
    private final DocumentQueryService documents;
    private final PesquisaPdfRenderer renderer;

    public PesquisaPdfService(DocumentQueryService documents, PesquisaPdfRenderer renderer) {
        this.documents = documents;
        this.renderer = renderer;
    }

    @Transactional(readOnly = true)
    public byte[] generate(PesquisaPdfRequest request) {
        var range = request.intervaloPaginas();
        if (range != null) {
            long totalPages = (range.totalResultados().longValue() + range.itensPorPagina() - 1) / range.itensPorPagina();
            long selectedCount = Math.min(range.ate().longValue() * range.itensPorPagina(), range.totalResultados())
                    - (range.de().longValue() - 1) * range.itensPorPagina();
            if (range.de() > range.ate() || range.ate() > totalPages || selectedCount != request.resultados().size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Intervalo de páginas incompatível com os resultados");
            }
        }
        if (request.dataDe() != null && request.dataAte() != null && request.dataDe().isAfter(request.dataAte())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período inválido");
        }
        Set<DocumentoReferencia> unique = new HashSet<>(request.resultados());
        if (unique.size() != request.resultados().size()
                || unique.stream().anyMatch(ref -> !request.categorias().contains(ref.categoria()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resultados incompatíveis com a pesquisa");
        }
        Map<DocumentoReferencia,DocumentQueryService.Summary> found = new HashMap<>();
        for (String category : new LinkedHashSet<>(request.categorias())) {
            var ids = request.resultados().stream().filter(ref -> ref.categoria().equals(category))
                    .map(DocumentoReferencia::id).toList();
            for (int start = 0; start < ids.size(); start += 250) {
                documents.summariesByIds(category, ids.subList(start, Math.min(start + 250, ids.size())))
                        .forEach(row -> found.put(new DocumentoReferencia(category, row.id()), row));
            }
        }
        if (found.size() != unique.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Um resultado não está mais disponível. Atualize a pesquisa e tente novamente.");
        }
        var ordered = request.resultados().stream().map(found::get).toList();
        try {
            return renderer.render(request, ordered, OffsetDateTime.now(ZoneId.of("America/Sao_Paulo")));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Não foi possível gerar o PDF. Tente novamente.", exception);
        }
    }
}
