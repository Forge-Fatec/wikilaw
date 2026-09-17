package forge.wikilaw.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import forge.wikilaw.backend.entity.CargaDados;
import forge.wikilaw.backend.entity.CargaStatus;
import forge.wikilaw.backend.entity.RegistroBruto;
import forge.wikilaw.backend.exception.DataJudParseException;
import forge.wikilaw.backend.integration.NormalizedProcessRecord;
import forge.wikilaw.backend.integration.datajud.DataJudClient;
import forge.wikilaw.backend.integration.datajud.DataJudMapper;
import forge.wikilaw.backend.integration.datajud.DataJudParser;
import forge.wikilaw.backend.integration.datajud.DataJudQueryFactory;
import forge.wikilaw.backend.integration.datajud.DataJudRawResponse;
import forge.wikilaw.backend.integration.datajud.DataJudTribunal;
import forge.wikilaw.backend.integration.datajud.dto.DataJudHitDto;
import forge.wikilaw.backend.integration.datajud.dto.DataJudImportRequest;
import forge.wikilaw.backend.integration.datajud.dto.DataJudResponseDto;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataJudImportServiceTest {

    @Mock CargaDadosService cargaService;
    @Mock RegistroBrutoService registroBrutoService;
    @Mock DataJudClient client;
    @Mock DataJudParser parser;
    @Mock DataJudQueryFactory queryFactory;
    @Mock DataJudMapper mapper;
    @Mock DataJudRecordProcessor recordProcessor;

    private DataJudImportService service;
    private CargaDados running;
    private CargaDados finished;
    private RegistroBruto rawText;

    @BeforeEach
    void setUp() {
        service = new DataJudImportService(
                cargaService, registroBrutoService, client, parser,
                queryFactory, mapper, recordProcessor);
        running = new CargaDados();
        running.setId(1L);
        finished = new CargaDados();
        finished.setId(1L);
        rawText = new RegistroBruto();
        rawText.setId(2L);
        when(cargaService.iniciar("DATAJUD")).thenReturn(running);
        when(queryFactory.create(any(), any())).thenReturn("{}");
        when(client.search(DataJudTribunal.TJSP, "{}"))
                .thenReturn(new DataJudRawResponse("{\"hits\":{\"hits\":[]}}"));
        when(registroBrutoService.salvarTextoOriginal(any(), anyString(), anyString()))
                .thenReturn(rawText);
    }

    @Test
    void savesRawTextBeforeParsingAndNormalization() {
        DataJudHitDto hit = new DataJudHitDto("external-1", null, List.of());
        DataJudResponseDto response = new DataJudResponseDto(
                new DataJudResponseDto.Hits(new DataJudResponseDto.Total(1, "eq"), List.of(hit)));
        RegistroBruto rawJson = new RegistroBruto();
        rawJson.setId(2L);
        NormalizedProcessRecord normalized = new NormalizedProcessRecord(
                "external-1", "TJSP", "40017037420268260360", "JE",
                null, 0, null, null, null, null, null, null, List.of(), List.of());
        when(parser.parse(anyString())).thenReturn(response);
        when(registroBrutoService.confirmarJsonValido(2L, "{\"hits\":{\"hits\":[]}}"))
                .thenReturn(rawJson);
        when(mapper.map(hit, DataJudTribunal.TJSP)).thenReturn(normalized);
        finished.setStatus(CargaStatus.CONCLUIDA);
        when(cargaService.concluir(1L, 1, 1, 0, null, false)).thenReturn(finished);

        var summary = service.importar(request());

        InOrder order = inOrder(registroBrutoService, parser, mapper, recordProcessor);
        order.verify(registroBrutoService).salvarTextoOriginal(any(), anyString(), anyString());
        order.verify(parser).parse(anyString());
        order.verify(registroBrutoService).confirmarJsonValido(anyLong(), anyString());
        order.verify(mapper).map(hit, DataJudTribunal.TJSP);
        order.verify(recordProcessor).processar(normalized, rawJson, DataJudTribunal.TJSP);
        assertThat(summary.status()).isEqualTo(CargaStatus.CONCLUIDA);
    }

    @Test
    void keepsRawTextAndMarksLoadFailedWhenResponseIsInvalid() {
        when(parser.parse(anyString())).thenThrow(new DataJudParseException("JSON inválido"));
        finished.setStatus(CargaStatus.FALHA);
        finished.setMensagemErro("JSON inválido");
        when(cargaService.concluir(anyLong(), anyInt(), anyInt(), anyInt(), anyString(), anyBoolean()))
                .thenReturn(finished);

        var summary = service.importar(request());

        InOrder order = inOrder(registroBrutoService, parser);
        order.verify(registroBrutoService).salvarTextoOriginal(any(), anyString(), anyString());
        order.verify(parser).parse(anyString());
        verify(registroBrutoService, org.mockito.Mockito.never()).confirmarJsonValido(anyLong(), anyString());
        assertThat(summary.status()).isEqualTo(CargaStatus.FALHA);
        assertThat(summary.erros()).isEqualTo(1);
    }

    private DataJudImportRequest request() {
        return new DataJudImportRequest(DataJudTribunal.TJSP, null, 10, 1);
    }
}
