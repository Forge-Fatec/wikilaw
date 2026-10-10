package forge.wikilaw.backend.integration.documents;

import static org.assertj.core.api.Assertions.*;
import forge.wikilaw.backend.integration.stj.*;
import forge.wikilaw.backend.integration.scielo.ScieloAdapter;
import forge.wikilaw.backend.integration.bdtd.BdtdAdapter;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class FullCollectionAdaptersTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private DocumentImportRequest request() {
        return new DocumentImportRequest(null,null,1,0,null,null,null,null,null,null,null).withFullCollection();
    }

    @Test
    void stjTraversesArchiveEveryJsonResourceAndEveryCourtDataset() throws Exception {
        String first="espelhos-de-acordaos-corte-especial", second="espelhos-de-acordaos-primeira-turma";
        var archive = new java.io.ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(archive)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("historico.json"));
            zip.write("[{\"id\":\"archive\"}]".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        String encoded = Base64.getEncoder().encodeToString(archive.toByteArray());
        AuditedFetch fetch = (url,body,format) -> {
            if (url.endsWith("package_list")) return payload("{\"success\":true,\"result\":[\""+second+"\",\""+first+"\",\"irrelevant\"]}");
            if (url.endsWith("id="+first)) return payload(catalog(resource("archive","ZIP","2020.zip"),
                    resource("first-json","JSON","2021.json"),resource("second-json","JSON","2022.json")));
            if (url.endsWith("id="+second)) return payload(catalog(resource("third-json","JSON","2023.json")));
            if (url.endsWith("2020.zip")) { assertThat(format).isEqualTo("ZIP_BASE64"); return payload(encoded); }
            return payload("[{\"id\":\""+url.substring(url.lastIndexOf('/')+1)+"\"}]");
        };
        var adapter = new StjAdapter(json,new StjCatalog(json));
        var current = request();
        var ids = new ArrayList<String>();
        for (int i=0;i<4;i++) {
            var page = adapter.collect(current,fetch);
            ids.add(page.registros().getFirst().path("id").asText());
            if (i<3) {
                assertThat(page.proxima()).isNotNull();
                current=DocumentContinuation.next(DocumentSource.STJ,current,page.proxima());
                assertThat(current.fullCollection()).isTrue();
            } else assertThat(page.proxima()).isNull();
        }
        assertThat(ids).containsExactly("archive","2021.json","2022.json","2023.json");
    }

    @Test
    void invalidArchiveDoesNotHideRemainingAccessibleFiles() {
        var adapter = new StjAdapter(json,new StjCatalog(json));
        var page = adapter.collect(request(),(url,body,format) -> {
            if (url.endsWith("package_list")) return payload("{\"success\":true,\"result\":[\"espelhos-de-acordaos-corte-especial\"]}");
            if (url.contains("package_show")) return payload(catalog(resource("archive","ZIP","2020.zip"),resource("next","JSON","2021.json")));
            return payload(Base64.getEncoder().encodeToString("invalid ZIP".getBytes(StandardCharsets.UTF_8)));
        });
        assertThat(page.falhasColeta()).isEqualTo(1);
        assertThat(page.avisos()).hasSize(1);
        assertThat(page.proxima().recursoId()).isEqualTo("next");
    }

    @Test
    void scieloFullCollectionDoesNotRestrictJournalOrCollection() {
        new ScieloAdapter(json).collect(request(),(url,body,format) -> {
            assertThat(url).doesNotContain("issn=","collection=");
            return payload("{\"meta\":{\"total\":0},\"objects\":[]}");
        });
    }

    @Test
    void bdtdFullCollectionKeepsNonLegalRecordsAndTokenContinuation() {
        var adapter=new BdtdAdapter(json);
        String xml="""
                <OAI-PMH xmlns="http://www.openarchives.org/OAI/2.0/" xmlns:dc="http://purl.org/dc/elements/1.1/">
                <ListRecords><record><header><identifier>oai:example:1</identifier></header>
                <metadata><dc:title>Biologia molecular</dc:title></metadata></record>
                <resumptionToken>next-token</resumptionToken></ListRecords></OAI-PMH>
                """;
        var page=adapter.collect(request(),(url,body,format) -> payload(xml));
        assertThat(page.registros()).hasSize(1);
        assertThat(page.proxima().resumptionToken()).isEqualTo("next-token");
        var next=DocumentContinuation.next(DocumentSource.BDTD,request(),page.proxima());
        assertThat(next.fullCollection()).isTrue();
    }

    private AuditedFetch.Payload payload(String text) { return new AuditedFetch.Payload(text,1L); }

    @Test
    void historicalZipLargerThan32MiBExpandedIsReadAsPagesAcrossEntries() throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();
        String padding="x".repeat(1024*1024);
        try (var zip=new java.util.zip.ZipOutputStream(bytes)) {
            for (int i=0;i<34;i++) {
                zip.putNextEntry(new java.util.zip.ZipEntry(i+".json"));
                zip.write(("[{\"id\":\"entry-"+i+"\",\"padding\":\""+padding+"\"}]").getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        String encoded=Base64.getEncoder().encodeToString(bytes.toByteArray());
        var adapter=new StjAdapter(json,new StjCatalog(json));
        AuditedFetch fetch=(url,body,format) -> {
            if (url.endsWith("package_list")) return payload("{\"success\":true,\"result\":[\"espelhos-de-acordaos-corte-especial\"]}");
            if (url.contains("package_show")) return payload(catalog(resource("archive","ZIP","2020.zip")));
            return payload(encoded);
        };
        var request=new DocumentImportRequest(null,null,2,32,null,null,null,null,null,null,null).withFullCollection();
        var page=adapter.collect(request,fetch);
        assertThat(page.falhasColeta()).isZero();
        assertThat(page.registros()).extracting(n -> n.path("id").asText()).containsExactly("entry-32","entry-33");
        assertThat(page.proxima()).isNull();
        var first=adapter.collect(request(),fetch);
        assertThat(first.proxima().offset()).isEqualTo(1);
    }

    @Test
    void pangeaVisitsAllOrganTypePartitionsWithoutTrustingCappedTotal() {
        var adapter = new forge.wikilaw.backend.integration.pangea.PangeaAdapter(json);
        var request = new DocumentImportRequest(null,0,2,null,null,null,null,null,null,null,null).withFullCollection();
        var visited = new ArrayList<String>();
        AuditedFetch fetch = (url,body,format) -> {
            if (url.endsWith("parametros")) return payload("{\"orgaos\":[{\"sigla\":\"STJ\"},{\"sigla\":\"TJMG\"}],\"especies\":[{\"sigla\":\"IRDR\"},{\"sigla\":\"IAC\"}]}");
            var filter = json.readTree(body).path("filtro");
            assertThat(filter.path("buscaGeral").asText()).isEmpty();
            assertThat(filter.path("cancelados").asBoolean()).isTrue();
            visited.add(filter.path("orgaos").get(0).asText()+":"+filter.path("tipos").get(0).asText());
            return payload("{\"total\":10000,\"resultados\":[{\"id\":\"one\"}]}");
        };
        for (int i=0;i<4;i++) {
            var page=adapter.collect(request,fetch);
            if (i<3) request=DocumentContinuation.next(DocumentSource.PANGEA,request,page.proxima());
            else assertThat(page.proxima()).isNull();
        }
        assertThat(visited).containsExactly("STJ:IRDR","STJ:IAC","TJMG:IRDR","TJMG:IAC");
    }
    private String resource(String id,String format,String name) {
        return "{\"id\":\""+id+"\",\"format\":\""+format+"\",\"name\":\""+name
                +"\",\"url\":\"https://dadosabertos.web.stj.jus.br/"+name+"\"}";
    }
    private String catalog(String... resources) {
        return "{\"success\":true,\"result\":{\"resources\":["+String.join(",",resources)+"]}}";
    }
}
