package forge.wikilaw.backend.service;

import forge.wikilaw.backend.config.JurisprudenciaBootstrapProperties;
import forge.wikilaw.backend.entity.CargaStatus;
import forge.wikilaw.backend.integration.documents.DocumentImportRequest;
import forge.wikilaw.backend.integration.documents.DocumentSource;
import forge.wikilaw.backend.repository.DecisaoJudicialRepository;
import forge.wikilaw.backend.repository.FonteDadosRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class JurisprudenciaBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(JurisprudenciaBootstrapService.class);
    private static final String FONTE = "TJDFT";

    private final JurisprudenciaBootstrapProperties properties;
    private final DocumentImportService importService;
    private final FonteDadosRepository fontes;
    private final DecisaoJudicialRepository decisoes;

    public JurisprudenciaBootstrapService(
            JurisprudenciaBootstrapProperties properties,
            DocumentImportService importService,
            FonteDadosRepository fontes,
            DecisaoJudicialRepository decisoes) {
        this.properties = properties;
        this.importService = importService;
        this.fontes = fontes;
        this.decisoes = decisoes;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            importarPaginas();
        } catch (RuntimeException exception) {
            log.error("Carga inicial de jurisprudência falhou; backend continuará disponível: {}",
                    exception.getMessage(), exception);
        }
    }

    void importarPaginas() {
        var fonte = fontes.findBySigla(FONTE)
                .orElseThrow(() -> new IllegalStateException("Fonte TJDFT não cadastrada"));
        long existentes = decisoes.countByIdFonteAndAtivoTrue(fonte.getId());
        if (properties.isSomenteSeVazio() && existentes > 0) {
            log.info("Carga inicial TJDFT ignorada: {} decisões ativas já cadastradas", existentes);
            return;
        }

        log.info("Iniciando carga automática TJDFT: termo='{}', páginas={}, tamanho={}",
                properties.getTermo(), properties.getPaginas(), properties.getTamanhoPagina());
        int processados = 0;
        for (int pagina = 0; pagina < properties.getPaginas(); pagina++) {
            var request = new DocumentImportRequest(
                    properties.getTermo(),
                    pagina,
                    properties.getTamanhoPagina(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
            var resultado = importService.importar(DocumentSource.TJDFT, request);
            processados += resultado.processados();
            log.info("Carga automática TJDFT página={} status={} recebidos={} processados={} erros={}",
                    pagina,
                    resultado.status(),
                    resultado.recebidos(),
                    resultado.processados(),
                    resultado.erros());

            if (resultado.status() == CargaStatus.FALHA || resultado.recebidos() == 0) {
                break;
            }
        }
        log.info("Carga automática TJDFT finalizada: {} decisões processadas", processados);
    }
}
