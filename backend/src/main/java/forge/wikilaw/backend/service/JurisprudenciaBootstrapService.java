package forge.wikilaw.backend.service;

import forge.wikilaw.backend.config.JurisprudenciaBootstrapProperties;
import forge.wikilaw.backend.integration.documents.DocumentImportRequest;
import forge.wikilaw.backend.integration.documents.DocumentSource;
import forge.wikilaw.backend.repository.DecisaoJudicialRepository;
import forge.wikilaw.backend.repository.DocumentoDoutrinarioRepository;
import forge.wikilaw.backend.repository.FonteDadosRepository;
import forge.wikilaw.backend.repository.PrecedenteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class JurisprudenciaBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(JurisprudenciaBootstrapService.class);
    private static final DocumentSource[] FONTES_DOCUMENTAIS = {
            DocumentSource.TJDFT,
            DocumentSource.STJ,
            DocumentSource.STJ_PRECEDENTES,
            DocumentSource.PANGEA,
            DocumentSource.BDJUR,
            DocumentSource.SCIELO,
            DocumentSource.BDTD
    };

    private final JurisprudenciaBootstrapProperties properties;
    private final ImportJobService importService;
    private final FonteDadosRepository fontes;
    private final DecisaoJudicialRepository decisoes;
    private final PrecedenteRepository precedentes;
    private final DocumentoDoutrinarioRepository doutrina;

    public JurisprudenciaBootstrapService(
            JurisprudenciaBootstrapProperties properties,
            ImportJobService importService,
            FonteDadosRepository fontes,
            DecisaoJudicialRepository decisoes,
            PrecedenteRepository precedentes,
            DocumentoDoutrinarioRepository doutrina) {
        this.properties = properties;
        this.importService = importService;
        this.fontes = fontes;
        this.decisoes = decisoes;
        this.precedentes = precedentes;
        this.doutrina = doutrina;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            importarPaginas();
        } catch (RuntimeException exception) {
            log.error("Carga inicial documental falhou; backend continuara disponivel: {}",
                    exception.getMessage(), exception);
        }
    }

    void importarPaginas() {
        log.debug("Preparando carga documental: modo={}, tamanho={}",
                properties.isCompleto() ? "acervo completo" : "amostra", properties.getTamanhoPagina());
        int enfileiradas = 0;
        for (DocumentSource fonte : FONTES_DOCUMENTAIS) {
            try {
                enfileiradas += importarFonte(fonte);
            } catch (RuntimeException exception) {
                log.warn("Não foi possível enfileirar fonte {}: {}", fonte, exception.getMessage());
            }
        }
        log.debug("Carga automática documental: {} fontes preparadas", enfileiradas);
    }

    // Reconcile only the initial plans, including a bounded job that was busy
    // during startup. Completed, failed or cancelled full jobs are not recreated.
    @Scheduled(initialDelay = 30000, fixedDelay = 30000)
    public void garantirCargaCompleta() {
        if (properties.isEnabled() && properties.isCompleto()) importarPaginas();
    }

    int importarFonte(DocumentSource source) {
        var fonte = fontes.findBySigla(source.sigla());
        if (fonte.isEmpty()) {
            log.warn("Carga automatica {} ignorada: fonte nao cadastrada", source);
            return 0;
        }

        long existentes = contarAtivos(source, fonte.get().getId());
        if (!properties.isCompleto() && properties.isSomenteSeVazio() && existentes > 0) {
            log.info("Carga automatica {} ignorada: {} documentos ativos ja cadastrados",
                    source, existentes);
            return 0;
        }

        if (properties.isCompleto()) {
            var job = importService.ensureDocumentsComplete(source, primeiraRequisicao(source).withFullCollection());
            return job != null && job.paginas() == 0 ? 1 : 0;
        }
        importService.enqueueDocuments(source, primeiraRequisicao(source), properties.getPaginas());
        return 1;
    }

    private long contarAtivos(DocumentSource source, Long fonteId) {
        return switch (source) {
            case TJDFT, STJ -> decisoes.countByIdFonteAndAtivoTrue(fonteId);
            case STJ_PRECEDENTES, PANGEA -> precedentes.countByIdFonteAndAtivoTrue(fonteId);
            case BDJUR, BDTD, SCIELO -> doutrina.countByIdFonteAndAtivoTrue(fonteId);
        };
    }

    private DocumentImportRequest primeiraRequisicao(DocumentSource source) {
        return switch (source) {
            case TJDFT, BDJUR, PANGEA -> new DocumentImportRequest(
                    properties.isCompleto() ? null : properties.getTermo(), 0, properties.getTamanhoPagina(),
                    null, null, null, null, null, null, null, null);
            case BDTD -> new DocumentImportRequest(
                    properties.isCompleto() ? null : properties.getTermo(), null, properties.getTamanhoPagina(),
                    0, null, null, null, null, null, null, null);
            case STJ, STJ_PRECEDENTES, SCIELO -> new DocumentImportRequest(
                    null, null, properties.getTamanhoPagina(),
                    0, null, null, null, null, null, null, null);
        };
    }

}
