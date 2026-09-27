package forge.wikilaw.backend.integration.documents;

import forge.wikilaw.backend.integration.pangea.PangeaAdapter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PangeaAdapterTest {
    private final JsonMapper json=JsonMapper.builder().build();
    private final PangeaAdapter adapter=new PangeaAdapter(json);
    private DocumentImportRequest request(int page) {
        return new DocumentImportRequest("direito",page,1,null,null,null,null,null,null,null,null);
    }
    private String catalog() {
        return "{\"orgaos\":[{\"sigla\":\"TJMG\"}],\"especies\":[{\"sigla\":\"IRDR\"}]}";
    }
    @Test void requestsCatalogThenSearchWithOneBasedPageAndReturnsLocalContinuation() {
        AtomicInteger calls=new AtomicInteger();
        var page=adapter.collect(request(0),(url,body,format)->{
            calls.incrementAndGet();
            if(url.endsWith("/parametros")) {
                assertNull(body);
                return new AuditedFetch.Payload(catalog(),10L);
            }
            assertTrue(url.endsWith("/precedentes"));
            var filter=json.readTree(body).path("filtro");
            assertEquals(1,filter.path("pagina").asInt());
            assertEquals(1,filter.path("tamanhoPagina").asInt());
            assertEquals("TJMG",filter.path("orgaos").path(0).asText());
            assertEquals("IRDR",filter.path("tipos").path(0).asText());
            assertFalse(filter.path("cancelados").asBoolean());
            assertEquals("direito",filter.path("buscaGeral").asText());
            return new AuditedFetch.Payload("{\"total\":2,\"resultados\":[{\"id\":\"a\"}]}",11L);
        });
        assertEquals(2,calls.get());
        assertEquals(11L,page.registroBrutoId());
        assertEquals(1,page.proxima().pagina());
    }
    @Test void finalAndEmptyPagesHaveNoContinuation() {
        var page=adapter.collect(request(1),(u,b,f)->new AuditedFetch.Payload(
            u.endsWith("/parametros")?catalog():"{\"total\":2,\"resultados\":[{}]}",1L));
        assertNull(page.proxima());
        assertTrue(adapter.collect(request(0),(u,b,f)->new AuditedFetch.Payload(
            u.endsWith("/parametros")?catalog():"{\"total\":0,\"resultados\":[]}",1L)).registros().isEmpty());
    }
    @Test void doesNotSilentlyAcceptAnInvalidCatalogOrEnvelope() {
        assertThrows(IllegalArgumentException.class,()->adapter.collect(request(0),
            (u,b,f)->new AuditedFetch.Payload("{}",1L)));
        assertThrows(IllegalArgumentException.class,()->adapter.collect(request(0),
            (u,b,f)->new AuditedFetch.Payload(u.endsWith("/parametros")?catalog():"{\"resultados\":[]}",1L)));
    }
    @Test void preservesOriginAndOriginalMetadataWithoutConfusingUpdateDateWithPublication() {
        var n=json.readTree("""
            {"id":"tjmg-irdr-1","orgao":"TJMG","tipo":"IRDR","nr":1,
             "questao":"<p>Questão jurídica</p>","tese":"<p>Tese completa</p>",
             "highlight":{"tese":"Trecho parcial"},"situacao":"Julgado",
             "ultimaAtualizacao":"06/04/2026",
             "processosParadigma":[{"numero":"Link Nota Técnica","link":"https://example.org"}]}
            """);
        var doc=adapter.normalize(n);
        assertEquals("TJMG",doc.tribunal());
        assertEquals("tjmg-irdr-1",doc.identificador());
        assertEquals("Tese completa",doc.tese());
        assertEquals("Questão jurídica",doc.resumo());
        assertNull(doc.dataPublicacao());
        assertNull(doc.dataJulgamento());
        assertNull(doc.processosRelacionados());
        assertTrue(doc.metadados().contains("processosParadigma"));
        assertEquals("https://pangeabnp.pdpj.jus.br/pesquisa?orgao=tjmg&tipo=IRDR&nr=1",doc.url());
    }
    @Test void keepsTechnicalNoteTypeAndRejectsMissingIdentity() {
        assertEquals("NT",adapter.normalize(json.readTree(
            "{\"id\":\"tjba-nt-1\",\"orgao\":\"TJBA\",\"tipo\":\"NT\",\"nr\":1}")).tipo());
        assertThrows(IllegalArgumentException.class,()->adapter.normalize(json.readTree(
            "{\"orgao\":\"STJ\",\"tipo\":\"RR\",\"nr\":1}")));
    }
    @Test void refusesConfidentialRecords() {
        assertThrows(UnavailableDocumentException.class,()->adapter.normalize(json.readTree(
            "{\"id\":\"a\",\"orgao\":\"STJ\",\"tipo\":\"RR\",\"nr\":1,\"segredoJustica\":true}")));
    }
}
