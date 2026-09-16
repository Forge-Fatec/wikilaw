package forge.wikilaw.backend.service;

import forge.wikilaw.backend.entity.*;
import forge.wikilaw.backend.integration.documents.*;
import forge.wikilaw.backend.integration.tjdft.TjdftAdapter;
import forge.wikilaw.backend.integration.bdtd.BdtdAdapter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DocumentImportServiceTest {
    private final CargaDadosService cargas=mock(CargaDadosService.class);
    private final RegistroBrutoService raw=mock(RegistroBrutoService.class);
    private final PublicSourceHttpClient http=mock(PublicSourceHttpClient.class);
    private final DocumentRecordProcessor processor=mock(DocumentRecordProcessor.class);
    private final JsonMapper json=JsonMapper.builder().build();
    private CargaDados carga;
    private final DocumentImportRequest request=new DocumentImportRequest(null,null,10,null,null,null,null,null,null,null,null);

    @BeforeEach void prepare() {
        FonteDados f=new FonteDados();f.setId(1L);f.setSigla("TJDFT");
        carga=new CargaDados();carga.setId(2L);carga.setFonte(f);
        when(cargas.iniciar(anyString())).thenReturn(carga);
        when(cargas.concluir(anyLong(),anyInt(),anyInt(),anyInt(),nullable(String.class),anyBoolean()))
            .thenAnswer(invocation -> {
                carga.setStatus(invocation.getArgument(5,Boolean.class)?CargaStatus.FALHA:
                    invocation.getArgument(3,Integer.class)>0?CargaStatus.CONCLUIDA_COM_ERROS:CargaStatus.CONCLUIDA);
                return carga;
            });
        RegistroBruto record=new RegistroBruto();record.setId(3L);
        when(raw.salvarTextoOriginal(any(),anyString(),anyString(),anyString())).thenReturn(record);
        when(processor.process(any(),anyLong(),anyLong(),any())).thenReturn(9L);
    }
    private DocumentImportService service(DocumentAdapter adapter) {
        return new DocumentImportService(List.of(adapter),cargas,raw,http,json,processor);
    }
    @Test void savesRawBeforeNormalizationAndContinuesAfterBadRecord() {
        when(http.request(anyString(),anyString())).thenReturn(new PublicSourceHttpClient.Response(200,
            "{\"hits\":3,\"registros\":[{\"uuid\":\"a\",\"identificador\":\"1\"},{\"identificador\":\"bad\"},{\"uuid\":\"c\",\"identificador\":\"3\"}]}","application/json"));
        var result=service(new TjdftAdapter(json)).importar(DocumentSource.TJDFT,request);
        assertEquals(CargaStatus.CONCLUIDA_COM_ERROS,result.status());
        assertEquals(3,result.recebidos());assertEquals(2,result.processados());assertEquals(1,result.erros());
        var order=inOrder(raw,processor);
        order.verify(raw).salvarTextoOriginal(any(),anyString(),anyString(),eq("JSON"));
        order.verify(raw).confirmarJsonValido(eq(3L),anyString());
        order.verify(processor,times(2)).process(any(),anyLong(),anyLong(),any());
    }
    @Test void malformedJsonIsPreservedButNeverMarkedValid() {
        when(http.request(anyString(),anyString())).thenReturn(new PublicSourceHttpClient.Response(200,"{broken","application/json"));
        var result=service(new TjdftAdapter(json)).importar(DocumentSource.TJDFT,request);
        assertEquals(CargaStatus.FALHA,result.status());
        verify(raw).salvarTextoOriginal(any(),anyString(),eq("{broken"),eq("JSON"));
        verify(raw,never()).confirmarJsonValido(anyLong(),anyString());
        verifyNoInteractions(processor);
    }
    @Test void htmlChallengeIsExplicitFailureAndIsStoredAsHtml() {
        when(http.request(anyString(),isNull())).thenReturn(new PublicSourceHttpClient.Response(200,
            "<html>Verificando navegador</html>","text/html"));
        var result=service(new BdtdAdapter(json)).importar(DocumentSource.BDTD,request);
        assertEquals(CargaStatus.FALHA,result.status());
        assertTrue(result.mensagemErro().contains("verificação"));
        verify(raw).salvarTextoOriginal(any(),anyString(),anyString(),eq("HTML"));
        verifyNoInteractions(processor);
    }
    @Test void rateLimitIsAuditedWithoutImmediateRetries() {
        when(http.request(anyString(),anyString())).thenReturn(new PublicSourceHttpClient.Response(429,"rate limited","text/plain"));
        var result=service(new TjdftAdapter(json)).importar(DocumentSource.TJDFT,request);
        assertEquals(CargaStatus.FALHA,result.status());
        assertTrue(result.mensagemErro().contains("429"));
        verify(http,times(1)).request(anyString(),anyString());
        verify(raw).salvarTextoOriginal(any(),anyString(),eq("rate limited"),eq("JSON"));
    }
    @Test void serverErrorRetriesAreBoundedAndEveryResponseIsAudited() {
        when(http.request(anyString(),anyString())).thenReturn(
            new PublicSourceHttpClient.Response(503,"unavailable","text/plain"),
            new PublicSourceHttpClient.Response(200,"{\"hits\":0,\"registros\":[]}","application/json"));
        var result=service(new TjdftAdapter(json)).importar(DocumentSource.TJDFT,request);
        assertEquals(CargaStatus.CONCLUIDA,result.status());
        verify(raw,times(2)).salvarTextoOriginal(any(),anyString(),anyString(),anyString());
        verify(http,times(2)).request(anyString(),anyString());
    }
    @Test void unusedSearchFilterIsRejectedBeforeOpeningLoad() {
        var invalid=new DocumentImportRequest("direito",null,1,null,null,null,null,null,null,null,null);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> service(new TjdftAdapter(json)).importar(DocumentSource.SCIELO,invalid));
        verify(cargas,never()).iniciar(anyString());
    }
    @Test void externalUrlsCannotTargetLocalOrUntrustedServices() {
        var client=new PublicSourceHttpClient();
        for(String url:List.of("http://localhost:8080","https://example.org","https://bdjur.stj.jus.br:444/x",
                "https://user@bdjur.stj.jus.br/x")) assertThrows(IllegalArgumentException.class,()->client.request(url,null));
    }
}
