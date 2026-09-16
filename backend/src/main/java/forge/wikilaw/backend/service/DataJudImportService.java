package forge.wikilaw.backend.service;

import forge.wikilaw.backend.entity.CargaDados;
import forge.wikilaw.backend.entity.RegistroBruto;
import forge.wikilaw.backend.integration.NormalizedProcessRecord;
import forge.wikilaw.backend.integration.datajud.DataJudClient;
import forge.wikilaw.backend.integration.datajud.DataJudMapper;
import forge.wikilaw.backend.integration.datajud.DataJudParser;
import forge.wikilaw.backend.integration.datajud.DataJudQueryFactory;
import forge.wikilaw.backend.integration.datajud.DataJudRawResponse;
import forge.wikilaw.backend.integration.datajud.dto.CargaResumoResponse;
import forge.wikilaw.backend.integration.datajud.dto.DataJudHitDto;
import forge.wikilaw.backend.integration.datajud.dto.DataJudImportRequest;
import forge.wikilaw.backend.integration.datajud.dto.DataJudResponseDto;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class DataJudImportService {

    private static final Logger log = LoggerFactory.getLogger(DataJudImportService.class);

    private final CargaDadosService cargaService;
    private final RegistroBrutoService registroBrutoService;
    private final DataJudClient client;
    private final DataJudParser parser;
    private final DataJudQueryFactory queryFactory;
    private final DataJudMapper mapper;
    private final DataJudRecordProcessor recordProcessor;

    public DataJudImportService(
            CargaDadosService cargaService,
            RegistroBrutoService registroBrutoService,
            DataJudClient client,
            DataJudParser parser,
            DataJudQueryFactory queryFactory,
            DataJudMapper mapper,
            DataJudRecordProcessor recordProcessor) {
        this.cargaService = cargaService;
        this.registroBrutoService = registroBrutoService;
        this.client = client;
        this.parser = parser;
        this.queryFactory = queryFactory;
        this.mapper = mapper;
        this.recordProcessor = recordProcessor;
    }

    public CargaResumoResponse importar(DataJudImportRequest request) {
        CargaDados carga = cargaService.iniciar("DATAJUD");
        int recebidos = 0;
        int processados = 0;
        int erros = 0;
        boolean falhaFatal = false;
        List<String> mensagensErro = new ArrayList<>();
        List<JsonNode> searchAfter = List.of();

        log.info("Iniciando carga DataJud id={} tribunal={} tamanhoPagina={} maximoPaginas={}",
                carga.getId(), request.tribunal(), request.tamanhoPaginaEfetivo(), request.maximoPaginasEfetivo());

        try {
            for (int pagina = 1; pagina <= request.maximoPaginasEfetivo(); pagina++) {
                String query = queryFactory.create(request, searchAfter);
                log.info("Consultando página DataJud carga={} tribunal={} página={}",
                        carga.getId(), request.tribunal(), pagina);

                DataJudRawResponse rawResponse = client.search(request.tribunal(), query);
                RegistroBruto registroTexto = registroBrutoService.salvarTextoOriginal(
                        carga,
                        "DATAJUD:%s:CARGA:%d:PAGINA:%d".formatted(request.tribunal(), carga.getId(), pagina),
                        rawResponse.payload());

                DataJudResponseDto response = parser.parse(rawResponse.payload());
                RegistroBruto registroBruto = registroBrutoService.confirmarJsonValido(
                        registroTexto.getId(), rawResponse.payload());

                List<DataJudHitDto> hits = response.hits().hits();
                recebidos += hits.size();
                log.info("Página DataJud recebida carga={} tribunal={} página={} registros={}",
                        carga.getId(), request.tribunal(), pagina, hits.size());

                for (DataJudHitDto hit : hits) {
                    try {
                        NormalizedProcessRecord normalized = mapper.map(hit, request.tribunal());
                        recordProcessor.processar(normalized, registroBruto, request.tribunal());
                        processados++;
                    } catch (RuntimeException exception) {
                        erros++;
                        String externalId = hit == null ? null : hit.id();
                        String message = "Registro %s: %s".formatted(externalId, safeMessage(exception));
                        mensagensErro.add(message);
                        log.warn("Falha ao normalizar registro DataJud carga={} tribunal={} registro={}: {}",
                                carga.getId(), request.tribunal(), externalId, exception.getMessage());
                    }
                }

                if (hits.size() < request.tamanhoPaginaEfetivo()) {
                    break;
                }
                List<JsonNode> nextCursor = hits.get(hits.size() - 1).sort();
                if (nextCursor == null || nextCursor.isEmpty() || nextCursor.equals(searchAfter)) {
                    log.warn("Paginação DataJud encerrada sem novo cursor carga={} tribunal={} página={}",
                            carga.getId(), request.tribunal(), pagina);
                    break;
                }
                searchAfter = List.copyOf(nextCursor);
            }
        } catch (RuntimeException exception) {
            falhaFatal = true;
            erros++;
            mensagensErro.add(safeMessage(exception));
            log.error("Carga DataJud falhou id={} tribunal={}: {}",
                    carga.getId(), request.tribunal(), exception.getMessage(), exception);
        }

        String mensagemErro = mensagensErro.isEmpty() ? null : String.join(" | ", mensagensErro);
        CargaDados finalizada = cargaService.concluir(
                carga.getId(), recebidos, processados, erros, mensagemErro, falhaFatal);
        log.info("Carga DataJud finalizada id={} tribunal={} status={} recebidos={} processados={} erros={}",
                carga.getId(), request.tribunal(), finalizada.getStatus(), recebidos, processados, erros);

        return new CargaResumoResponse(
                carga.getId(),
                "DATAJUD",
                request.tribunal().name(),
                finalizada.getStatus(),
                recebidos,
                processados,
                erros,
                finalizada.getMensagemErro());
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
