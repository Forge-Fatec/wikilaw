package forge.wikilaw.backend.service;

import forge.wikilaw.backend.entity.CargaStatus;
import forge.wikilaw.backend.entity.ImportJob;
import forge.wikilaw.backend.entity.ImportJob.Status;
import forge.wikilaw.backend.integration.datajud.dto.DataJudImportRequest;
import forge.wikilaw.backend.integration.documents.*;
import forge.wikilaw.backend.repository.ImportJobRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ImportJobProcessor {
    private final ImportJobRepository jobs;
    private final DataJudImportService datajud;
    private final DocumentImportService documents;
    private final ObjectMapper json;

    public ImportJobProcessor(ImportJobRepository jobs, DataJudImportService datajud,
            DocumentImportService documents, ObjectMapper json) {
        this.jobs = jobs;
        this.datajud = datajud;
        this.documents = documents;
        this.json = json;
    }

    // Short transaction makes EM_EXECUCAO visible before the HTTP request starts.
    @Transactional
    public Optional<Long> prepareNext() {
        return jobs.lockNext().map(job -> {
            job.setStatus(Status.EM_EXECUCAO);
            return job.getId();
        });
    }

    @Transactional
    public void processPage(Long id) {
        var available = jobs.lockAvailable(id);
        if (available.isEmpty()) return;
        var job = available.get();
        if (job.getStatus() != Status.EM_EXECUCAO && job.getStatus() != Status.PENDENTE) return;
        try {
            if ("DATAJUD".equals(job.getFonte())) processDataJud(job);
            else processDocuments(job);
        } catch (RuntimeException exception) {
            job.setStatus(Status.FALHA);
            job.setMensagem(limit(exception.getMessage()));
        }
        job.setAtualizadoEm(ImportJobService.now());
    }

    private void processDataJud(ImportJob job) {
        var request = json.readValue(job.getRequisicao(), DataJudImportRequest.class);
        List<JsonNode> cursor = job.getCursorJson() == null ? List.of()
                : json.readValue(job.getCursorJson(), new TypeReference<List<JsonNode>>() {});
        // NOT_SUPPORTED suspends only the job transaction; its row lock remains
        // held while each raw response and business record commits independently.
        var result = datajud.importarPagina(request, cursor);
        var summary = result.resumo();
        job.setUltimaCarga(summary.idCarga());
        if (summary.status() == CargaStatus.FALHA) {
            fail(job, summary.mensagemErro());
            return; // Keep cursor and totals of completed pages unchanged on replay.
        }
        boolean more = !result.proxima().isEmpty();
        job.setCursorJson(more ? json.writeValueAsString(result.proxima()) : null);
        checkpoint(job, summary.recebidos(), summary.processados(), summary.erros(), summary.mensagemErro(), more);
    }

    private void processDocuments(ImportJob job) {
        var source = DocumentSource.valueOf(job.getFonte());
        var request = json.readValue(job.getRequisicao(), DocumentImportRequest.class);
        Map<String, Long> cache = json.readValue(job.getCacheJson(), new TypeReference<Map<String, Long>>() {});
        var result = documents.importar(source, request, cache);
        job.setCacheJson(json.writeValueAsString(cache));
        job.setUltimaCarga(result.idCarga());
        if (result.status() == CargaStatus.FALHA) {
            fail(job, result.mensagemErro());
            return;
        }
        boolean more = result.proxima() != null;
        if (more) {
            var next = DocumentContinuation.next(source, request, result.proxima());
            String serialized = json.writeValueAsString(next);
            if (serialized.equals(job.getRequisicao())) {
                fail(job, "Fonte retornou a mesma continuação; verifique a paginação antes de retomar");
                return;
            }
            job.setRequisicao(serialized);
        }
        String message = result.mensagemErro();
        if (!result.avisos().isEmpty()) message = (message == null ? "" : message + " | ") + String.join(" | ", result.avisos());
        checkpoint(job, result.recebidos(), result.processados(), result.erros(), message, more);
    }

    private void checkpoint(ImportJob job, int received, int processed, int errors, String message, boolean more) {
        job.setPaginas(job.getPaginas() + 1);
        job.setRecebidos(job.getRecebidos() + received);
        job.setProcessados(job.getProcessados() + processed);
        job.setErros(job.getErros() + errors);
        if (message != null) job.setMensagem(limit(message));
        job.setStatus(!more ? (job.getErros() > 0 ? Status.CONCLUIDA_COM_ERROS : Status.CONCLUIDA)
                : !job.isAteEsgotar() && job.getPaginas() >= job.getMaximoPaginas() ? Status.LIMITE_ATINGIDO : Status.EM_EXECUCAO);
    }

    private void fail(ImportJob job, String message) {
        job.setStatus(Status.FALHA);
        job.setMensagem(limit(message));
    }

    private String limit(String message) {
        if (message == null) return "Falha na importação; consulte a última carga";
        return message.substring(0, Math.min(message.length(), 2000));
    }
}
