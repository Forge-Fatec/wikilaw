package forge.wikilaw.backend.service;

import forge.wikilaw.backend.dto.ImportJobResponse;
import forge.wikilaw.backend.entity.ImportJob;
import forge.wikilaw.backend.entity.ImportJob.Status;
import forge.wikilaw.backend.integration.datajud.dto.DataJudImportRequest;
import forge.wikilaw.backend.integration.documents.*;
import forge.wikilaw.backend.repository.ImportJobRepository;
import jakarta.validation.Validator;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ImportJobService {
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private final ImportJobRepository jobs;
    private final DocumentImportService documents;
    private final ObjectMapper json;
    private final Validator validator;

    public ImportJobService(ImportJobRepository jobs, DocumentImportService documents,
            ObjectMapper json, Validator validator) {
        this.jobs = jobs;
        this.documents = documents;
        this.json = json;
        this.validator = validator;
    }

    @Transactional
    public ImportJobResponse enqueueDataJud(DataJudImportRequest request) {
        return enqueueDataJud(request, false);
    }

    @Transactional
    public ImportJobResponse enqueueDataJud(DataJudImportRequest request, boolean incremental) {
        return enqueueDataJud(request, incremental, false);
    }

    @Transactional
    public ImportJobResponse enqueueDataJud(DataJudImportRequest request, boolean incremental, boolean ateEsgotar) {
        validate(request);
        var since = request.desde();
        if (incremental) {
            if (request.numeroProcesso() != null || request.desde() != null || request.ate() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "incremental não aceita numeroProcesso, desde ou ate; use uma janela explícita para esses filtros");
            }
            var previous = jobs.findTopByEscopoAndStatusAndIncrementalBaseTrueOrderByAtualizadoEmDesc(
                    "DATAJUD:" + request.tribunal(), Status.CONCLUIDA);
            if (previous.isPresent()) {
                var prior = json.readValue(previous.get().getRequisicao(), DataJudImportRequest.class);
                if (prior.numeroProcesso() == null && prior.ate() != null) {
                    // Overlap tolerates recently indexed updates; natural keys make replay safe.
                    since = prior.ate().minusMinutes(5);
                }
            }
        }
        // Freeze the upper bound so a long import does not chase newly arriving data.
        var bounded = new DataJudImportRequest(request.tribunal(), request.numeroProcesso(),
                request.tamanhoPagina(), request.maximoPaginas(), since,
                request.ate() == null ? now() : request.ate());
        validate(bounded);
        return enqueue("DATAJUD", "DATAJUD:" + request.tribunal(), bounded, request.maximoPaginasEfetivo(),
                request.numeroProcesso() == null && request.desde() == null && request.ate() == null, ateEsgotar);
    }

    @Transactional
    public ImportJobResponse enqueueDocuments(DocumentSource source, DocumentImportRequest request, int pages) {
        return enqueueDocuments(source,request,pages,request.fullCollection());
    }

    @Transactional
    public ImportJobResponse enqueueDocuments(DocumentSource source, DocumentImportRequest request, int pages, boolean ateEsgotar) {
        validate(request);
        documents.validate(source, request);
        return enqueue(source.name(), source.name(), request, pages, false, ateEsgotar);
    }

    /** Idempotent initial full harvest; never restart an existing full job on boot. */
    @Transactional
    public ImportJobResponse ensureDocumentsComplete(DocumentSource source, DocumentImportRequest request) {
        var existing = existingFullOrBusy(source.name());
        if (existing != null) return existing;
        var created = enqueueDocuments(source,request.withFullCollection(),1,true);
        jobs.findById(created.idTarefa()).orElseThrow().setBootstrapCompleto(true);
        return created;
    }

    @Transactional
    public ImportJobResponse ensureDataJudComplete(DataJudImportRequest request) {
        var existing = existingFullOrBusy("DATAJUD:" + request.tribunal());
        if (existing != null) return existing;
        var created = enqueueDataJud(request,false,true);
        jobs.findById(created.idTarefa()).orElseThrow().setBootstrapCompleto(true);
        return created;
    }

    private ImportJobResponse existingFullOrBusy(String scope) {
        var full = jobs.findTopByEscopoAndBootstrapCompletoTrueOrderByCriadoEmDesc(scope);
        if (full.isPresent()) return ImportJobResponse.from(full.get());
        var previous = jobs.findFirstByEscopoAndStatusIn(scope, java.util.List.of(
                Status.PENDENTE,Status.EM_EXECUCAO,Status.FALHA,Status.LIMITE_ATINGIDO));
        if (previous.isEmpty()) return null;
        var available = jobs.lockAvailable(previous.get().getId());
        if (available.isEmpty()) return ImportJobResponse.from(previous.get());
        // The full harvest starts at the beginning without keeping the old term
        // or cursor. Preserve all imported records and the previous audit trail.
        var old = available.get();
        entityManager.refresh(old);
        old.setStatus(Status.CANCELADA);
        old.setMensagem("Substituída por carga completa automática; dados anteriores preservados");
        old.setAtualizadoEm(now());
        jobs.flush();
        return null;
    }

    private ImportJobResponse enqueue(String source, String scope, Object request, int pages, boolean incrementalBase, boolean ateEsgotar) {
        validatePages(pages);
        var job = new ImportJob();
        job.setFonte(source);
        job.setEscopo(scope);
        job.setIncrementalBase(incrementalBase);
        job.setAteEsgotar(ateEsgotar);
        job.setRequisicao(json.writeValueAsString(request));
        job.setMaximoPaginas(pages);
        job.setCriadoEm(now());
        job.setAtualizadoEm(job.getCriadoEm());
        try {
            return ImportJobResponse.from(jobs.saveAndFlush(job));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Já existe uma tarefa ativa ou retomável para esta fonte; consulte, retome ou cancele a tarefa", exception);
        }
    }

    @Transactional(readOnly = true)
    public ImportJobResponse get(Long id) {
        return ImportJobResponse.from(jobs.findById(id).orElseThrow(() -> missing(id)));
    }

    @Transactional
    public ImportJobResponse resume(Long id, int additionalPages) {
        validatePages(additionalPages);
        var job = jobs.lockById(id).orElseThrow(() -> missing(id));
        if (job.getStatus() != Status.FALHA && job.getStatus() != Status.LIMITE_ATINGIDO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Somente tarefas com falha ou limite atingido podem ser retomadas");
        }
        if (!job.isAteEsgotar() && job.getPaginas() + additionalPages > 100000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limite total de 100000 páginas por tarefa");
        }
        if (!job.isAteEsgotar()) job.setMaximoPaginas((int) Math.max(job.getMaximoPaginas(), job.getPaginas() + additionalPages));
        job.setStatus(Status.PENDENTE);
        job.setMensagem(null);
        job.setAtualizadoEm(now());
        return ImportJobResponse.from(job);
    }

    @Transactional
    public ImportJobResponse cancel(Long id) {
        // Do not wait behind a page downloading a large file.
        var job = jobs.lockAvailable(id).orElseThrow(() -> {
            if (!jobs.existsById(id)) return missing(id);
            return new ResponseStatusException(HttpStatus.CONFLICT, "Página em processamento; tente cancelar novamente após seu checkpoint");
        });
        if (job.getStatus() == Status.CONCLUIDA || job.getStatus() == Status.CONCLUIDA_COM_ERROS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tarefa já concluída");
        }
        job.setStatus(Status.CANCELADA);
        job.setAtualizadoEm(now());
        return ImportJobResponse.from(job);
    }

    private void validate(Object request) {
        var errors = validator.validate(request);
        if (!errors.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                errors.iterator().next().getMessage());
    }

    private void validatePages(int pages) {
        if (pages < 1 || pages > 100000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "maximoPaginas deve estar entre 1 e 100000");
    }

    private ResponseStatusException missing(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarefa não encontrada: " + id);
    }

    static OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
}
