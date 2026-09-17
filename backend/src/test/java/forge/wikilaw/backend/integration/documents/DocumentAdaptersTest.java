package forge.wikilaw.backend.integration.documents;

import forge.wikilaw.backend.integration.tjdft.TjdftAdapter;
import forge.wikilaw.backend.integration.stj.*;
import forge.wikilaw.backend.integration.bdjur.BdjurAdapter;
import forge.wikilaw.backend.integration.bdtd.BdtdAdapter;
import forge.wikilaw.backend.integration.scielo.ScieloAdapter;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class DocumentAdaptersTest {
    private final JsonMapper json=JsonMapper.builder().build();
    private DocumentImportRequest request(Integer offset,Integer size) {
        return new DocumentImportRequest(null,null,size,offset,null,null,null,null,null,null,null);
    }
    @Test void tjdftDoesNotClaimUnavailableFullTextEvenWhenFlagIsTrue() {
        var adapter=new TjdftAdapter(json);
        var doc=adapter.normalize(json.readTree("""
            {"uuid":"abc","identificador":"2170687","processo":"0702497-45.2026.8.07.0007",
             "dataPublicacao":"2026-09-16T00:45:24Z","ementa":"Ementa legível","nomeRelator":"Relatora",
             "inteiroTeorHtml":"Inteiro Teor indisponível.","possuiInteiroTeor":true}
            """));
        assertNull(doc.inteiroTeor());
        assertEquals("Ementa legível",doc.resumo());
        assertEquals(LocalDate.of(2026,9,16),doc.dataPublicacao());
    }
    @Test void tjdftPaginationUsesHitsAndRejectsBadEnvelope() {
        var adapter=new TjdftAdapter(json);
        var page=adapter.collect(request(null,1),(url,body,format) -> {
            assertEquals(0,json.readTree(body).path("pagina").asInt());
            return new AuditedFetch.Payload("{\"hits\":{\"value\":2},\"registros\":[{\"uuid\":\"a\"}]}",7L);
        });
        assertEquals(1,page.proxima().pagina());
        assertThrows(IllegalArgumentException.class,() -> adapter.collect(request(null,1),
            (url,body,format) -> new AuditedFetch.Payload("{}",7L)));
    }
    @Test void stjPreservesDecisionSeparatelyFromFullOpinion() {
        var adapter=new StjAdapter(json,new StjCatalog(json));
        var doc=adapter.normalize(json.readTree("""
            {"id":"1","siglaClasse":"REsp","numeroProcesso":"123","dataDecisao":"20260805",
             "dataPublicacao":"DJEN DATA:17/08/2026","decisao":"Negar provimento","ementa":"Direito"}
            """));
        assertEquals(LocalDate.of(2026,8,17),doc.dataPublicacao());
        assertEquals(LocalDate.of(2026,8,5),doc.dataJulgamento());
        assertEquals("Negar provimento",doc.decisao());
        assertNull(doc.inteiroTeor());
    }
    @Test void csvHandlesQuotedCommasNewlinesAndBom() {
        var adapter=new StjPrecedentesAdapter(json,new StjCatalog(json));
        String csv="\uFEFFsequencialPrecedente,tipoPrecedente,numeroPrecedente,questaoSubmetidaAJulgamento,teseFirmada\r\n"
            +"1,Tema,12,\"Dano, moral\nsegunda linha\",\"Texto com \"\"aspas\"\"\"\r\n";
        var rows=adapter.parseCsv(csv);
        assertEquals(1,rows.size());
        var doc=adapter.normalize(rows.get(0));
        assertEquals("Dano, moral\nsegunda linha",doc.resumo());
        assertEquals("Texto com \"aspas\"",doc.tese());
        assertThrows(IllegalArgumentException.class,() -> adapter.parseCsv("erro,coluna\na,b"));
    }
    @Test void bdjurPreservesPartialDateAndTypeInsteadOfInventingBookContents() {
        var adapter=new BdjurAdapter(json);
        var doc=adapter.normalize(json.readTree("""
            {"uuid":"id","handle":"2011/33144","name":"Direito",
             "metadata":{"dc.contributor.author":[{"value":"Pessoa A"},{"value":"Pessoa B"}],
              "dc.date.issued":[{"value":"2010"}],"dc.type":[{"value":"Sumário de livro"}],
              "dc.description":[{"value":"Disponível somente sumário"}]}}
            """));
        assertEquals("Pessoa A; Pessoa B",doc.autores());
        assertEquals("2010",doc.dataOriginal());
        assertNull(doc.dataPublicacao());
        assertEquals("Sumário de livro",doc.tipo());
    }
    @Test void bdjurEmptySearchIsValidAndPaginationIsReadFromNestedPage() {
        var adapter=new BdjurAdapter(json);
        var page=adapter.collect(request(null,1),(u,b,f)->new AuditedFetch.Payload(
            "{\"_embedded\":{\"searchResult\":{\"page\":{\"totalElements\":0,\"totalPages\":0}}}}",1L));
        assertTrue(page.registros().isEmpty());
        assertNull(page.proxima());
    }
    @Test void scieloChoosesPortugueseAndKeepsIncompleteDate() {
        var adapter=new ScieloAdapter(json);
        var doc=adapter.normalize(json.readTree("""
            {"code":"S1808-24322008000200006","article":{
             "v12":[{"l":"en","_":"Justice"},{"l":"pt","_":"Acesso à <b>justiça</b>"}],
             "v83":[{"l":"pt","a":"Resumo"}],"v10":[{"n":"Ludmila","s":"Ribeiro"}],
             "v65":[{"_":"20081200"}],"v35":[{"_":"1808-2432"}]}}
            """));
        assertEquals("Acesso à justiça",doc.titulo());
        assertEquals("Ludmila Ribeiro",doc.autores());
        assertEquals("20081200",doc.dataOriginal());
        assertNull(doc.dataPublicacao());
        assertTrue(doc.url().contains("S1808-24322008000200006"));
    }
    @Test void scieloDefaultsToLawJournalAndReturnsOffset() {
        var page=new ScieloAdapter(json).collect(request(0,1),(url,body,format)->{
            assertTrue(url.contains("issn=1808-2432"));
            assertTrue(url.contains("body=false"));
            return new AuditedFetch.Payload("{\"meta\":{\"total\":2},\"objects\":[{}]}",1L);
        });
        assertEquals(1,page.proxima().offset());
    }
    @Test void stjResourceSelectionIsPinnedAndRejectsForeignHost() {
        var catalog=new StjCatalog(json);
        var resources=DocumentFields.array(json.readTree("""
            [{"id":"old","name":"20260101.json","format":"JSON"},
             {"id":"new","name":"20260831.json","format":"JSON"},
             {"id":"zip","name":"historico.zip","format":"ZIP"}]
            """),"fixture");
        assertEquals("new",catalog.select(resources,null,"JSON",null).path("id").asText());
        assertEquals("old",catalog.select(resources,"old","JSON",null).path("id").asText());
        assertThrows(IllegalArgumentException.class,() -> StjCatalog.download(json.readTree("{\"url\":\"https://example.org/file.json\"}")));
    }
    @Test void bdtdXmlNamespacesAndToken() {
        var adapter=new BdtdAdapter(json);
        var parsed=adapter.parse(oai());
        assertEquals("next+token",parsed.token());
        assertEquals(2,parsed.records().size());
        assertThrows(UnavailableDocumentException.class,()->adapter.normalize(parsed.records().get(1)));
        var doc=adapter.normalize(parsed.records().get(0));
        assertEquals("Direito constitucional",doc.titulo());
        assertEquals("Autora",doc.autores());
        assertEquals("oai:repo:1",doc.identificador());
    }
    @Test void incompleteStjProcessRowsDoNotDiscardValidThemes() {
        var adapter=new StjPrecedentesAdapter(json,new StjCatalog(json));
        var page=adapter.collect(request(0,1),(url,body,format)-> {
            if (url.contains("package_show")) return new AuditedFetch.Payload("""
                {"success":true,"result":{"resources":[
                {"id":"themes","name":"Temas.csv","format":"CSV","url":"https://dadosabertos.web.stj.jus.br/temas.csv"},
                {"id":"processes","name":"Processos.csv","format":"CSV","url":"https://dadosabertos.web.stj.jus.br/processos.csv"}]}}
                """,1L);
            if (url.endsWith("temas.csv")) return new AuditedFetch.Payload(
                "sequencialPrecedente,tipoPrecedente,numeroPrecedente\\n1,Tema,1\\n".replace("\\n","\n"),2L);
            return new AuditedFetch.Payload(
                "sequencialPrecedente,numeroRegistro,Processo\\n1,2026001,REsp 1\\n,,\\n".replace("\\n","\n"),3L);
        });
        assertEquals(1,page.registros().size());
        assertEquals(1,page.avisos().size());
        assertEquals(1,adapter.normalize(page.registros().get(0)).processosRelacionados().size());
    }
    @Test void bdtdRejectsBrowserChallengeXxeAndOaiErrors() {
        var adapter=new BdtdAdapter(json);
        assertThrows(IllegalArgumentException.class,()->adapter.parse("<html>Verificando navegador</html>"));
        assertThrows(IllegalArgumentException.class,()->adapter.parse(
            "<!DOCTYPE root [<!ENTITY xxe SYSTEM 'file:///never-read'>]><root>&xxe;</root>"));
        assertThrows(IllegalArgumentException.class,()->adapter.parse(
            "<OAI-PMH xmlns='http://www.openarchives.org/OAI/2.0/'><error code='badResumptionToken'/></OAI-PMH>"));
        assertTrue(adapter.parse(
            "<OAI-PMH xmlns='http://www.openarchives.org/OAI/2.0/'><error code='noRecordsMatch'/></OAI-PMH>").records().isEmpty());
    }
    @Test void bdtdContinuationTokenIsEncodedAndProtocolFieldsNotRepeated() {
        var r=new DocumentImportRequest(null,null,2,0,null,null,null,"token+ /",null,null,null);
        var page=new BdtdAdapter(json).collect(r,(url,body,format)->{
            assertTrue(url.contains("token%2B+%2F"));
            assertFalse(url.contains("metadataPrefix"));
            return new AuditedFetch.Payload(oai(),1L);
        });
        assertEquals("next+token",page.proxima().resumptionToken());
    }
    private String oai() {
        return """
            <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/">
            <ListRecords><record><header><identifier>oai:repo:1</identifier></header><metadata>
            <oai_dc:dc xmlns:oai_dc="http://www.openarchives.org/OAI/2.0/oai_dc/" xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>Direito constitucional</dc:title><dc:creator>Autora</dc:creator><dc:date>2024</dc:date>
            <dc:identifier>https://repository.example/item</dc:identifier>
            </oai_dc:dc></metadata></record>
            <record><header status="deleted"><identifier>oai:repo:2</identifier></header></record>
            <resumptionToken>next+token</resumptionToken></ListRecords></OAI-PMH>
            """;
    }
}
