package forge.wikilaw.backend.service;

import forge.wikilaw.backend.entity.*;
import forge.wikilaw.backend.integration.documents.*;
import forge.wikilaw.backend.repository.*;
import forge.wikilaw.backend.service.semantic.*;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Run against PostgreSQL with pgvector and the actual local model, not a cosine stub. */
@EnabledIfEnvironmentVariable(named="WIKILAW_VECTOR_TEST_JDBC_URL",matches=".+")
@SpringBootTest(properties={
    "spring.datasource.url=${WIKILAW_VECTOR_TEST_JDBC_URL}","spring.datasource.username=postgres","spring.datasource.password=postgres",
    "spring.datasource.hikari.maximum-pool-size=4","spring.flyway.enabled=${WIKILAW_VECTOR_TEST_FLYWAY:true}","spring.jpa.hibernate.ddl-auto=validate",
    "wikilaw.vector.enabled=true","wikilaw.vector.initial-delay=3600000",
    "wikilaw.vector.service-url=${WIKILAW_VECTOR_TEST_EMBEDDING_URL:http://localhost:8001}"
})
@AutoConfigureMockMvc(addFilters=false)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VectorPostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DocumentRecordProcessor processor;
    @Autowired FonteDadosRepository sources;
    @Autowired RegistroBrutoRepository rawRecords;
    @Autowired VectorIndexService index;
    @Autowired VectorStore vectors;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    static Long healthId;

    @Test @Order(1) void reportsIndexPreparationInsteadOfReturningTextualResultsAsVectors() throws Exception {
        mvc.perform(post("/api/pesquisas/similaridade").contentType(MediaType.APPLICATION_JSON).content("{\"termo\":\"cirurgia\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("error").value(org.hamcrest.Matchers.containsString("indexados")));
    }

    @Test @Order(2) void retrievesPortugueseParaphrasesWithRealEmbeddingsAndDeduplicatesDocuments() throws Exception {
        healthId=create("plano", "Negativa de cobertura assistencial",
                "Operadora de assistência à saúde. Recusa de procedimento cirúrgico prescrito. Tratamento de emergência. Obrigação de custear a intervenção.");
        create("rural", "Arrendamento de imóvel rural", "Contrato agrário. Pagamento do aluguel da fazenda. Colheita de soja e uso de trator.");
        create("tributo", "Imposto sobre exportação", "Tributação de mercadorias na alfândega. Recolhimento fiscal sobre comércio internacional.");
        assertThat(vectors.counts().pendentes()).isPositive();
        index.prepareModel();
        while (index.indexBatch()>0) { /* bounded batches until the fixture corpus is ready */ }
        assertThat(index.indexBatch()).isZero();
        var response=mvc.perform(post("/api/pesquisas/similaridade").contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\":\"Meu convênio se negou a pagar a operação urgente recomendada pelo médico.\",\"limite\":10}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("itens[0].documento.id").value(healthId.intValue()))
                .andExpect(jsonPath("itens[0].trecho").isNotEmpty()).andReturn().getResponse();
        var items=json.readTree(response.getContentAsString()).get("itens");
        var ids=new java.util.HashSet<Long>();
        for (var item:items) assertThat(ids.add(item.get("documento").get("id").asLong())).isTrue();
        assertThat(vectors.counts().pendentes()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trecho_embedding WHERE vector_dims(embedding)=384",Long.class)).isPositive();
    }

    @Test @Order(3) void staleAndWithdrawnDocumentsAreNotSearchableAndTheirVectorsAreRebuilt() throws Exception {
        jdbc.update("UPDATE trecho_semantico SET hash_texto=? WHERE id_documento_semantico IN (SELECT id_documento_semantico FROM documento_semantico WHERE id_documento_doutrinario=?)","0".repeat(64),healthId);
        assertThat(vectors.counts().pendentes()).isPositive();
        while (index.indexBatch()>0) { }
        assertThat(vectors.counts().pendentes()).isZero();
        processor.indisponibilizar(DocumentSource.BDJUR,sources.findBySigla("BDJUR").orElseThrow().getId(),"plano");
        mvc.perform(post("/api/pesquisas/similaridade").contentType(MediaType.APPLICATION_JSON).content("{\"termo\":\"cirurgia médica\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("itens[*].documento.id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(healthId.intValue()))));
    }

    @Test @Order(5) void modelVersionChangesRequeueTheActiveCorpusOnlyOnce() {
        long before=jdbc.queryForObject("SELECT count(*) FROM fila_embedding",Long.class);
        jdbc.update("UPDATE modelo_embedding SET modelo='old-revision' WHERE id=1");
        index.prepareModel();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fila_embedding",Long.class)).isGreaterThan(before);
        while(index.indexBatch()>0) { }
        index.prepareModel();
        assertThat(index.indexBatch()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fila_embedding",Long.class)).isZero();
    }

    private Long create(String id,String title,String text) {
        var source=sources.findBySigla("BDJUR").orElseThrow();
        var raw=new RegistroBruto(); raw.setFonte(source); raw.setFormatoPayload("JSON");raw.setPayloadTexto("{}");
        raw.setDataColeta(OffsetDateTime.now());raw.setHashConteudo("e".repeat(64));raw=rawRecords.saveAndFlush(raw);
        return processor.process(DocumentSource.BDJUR,source.getId(),raw.getId(),NormalizedDocument.builder()
                .identificador(id).titulo(title).tipo("ARTIGO").resumo(text).metadados("{}").build());
    }

    @Test @Order(4) void dashboardAndPdfUseTheSelectedVectorCandidatesWithoutALexicalQuery() throws Exception {
        var source=sources.findBySigla("STJ").orElseThrow();
        var raw=new RegistroBruto();raw.setFonte(source);raw.setFormatoPayload("JSON");raw.setPayloadTexto("{}");
        raw.setDataColeta(OffsetDateTime.now());raw.setHashConteudo("f".repeat(64));raw=rawRecords.saveAndFlush(raw);
        Long id=processor.process(DocumentSource.STJ,source.getId(),raw.getId(),NormalizedDocument.builder()
                .identificador("acordao-saude").titulo("Intervenção cirúrgica de emergência").tipo("ACORDAO")
                .resumo("Obrigação de cobertura assistencial").decisao("Recurso provido").metadados("{}").build());
        mvc.perform(post("/api/dashboard/resultados").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("idsDecisoes",java.util.List.of(id)))))
                .andExpect(status().isOk()).andExpect(jsonPath("indicadores.totalDecisoes").value(1));
        var request=new forge.wikilaw.backend.dto.PesquisaPdfRequest("",java.util.List.of("decisoes"),null,null,null,null,
                "relevancia",java.util.List.of(new forge.wikilaw.backend.dto.PesquisaPdfRequest.DocumentoReferencia("decisoes",id)),
                null,"caso.pdf",null,"similaridade");
        byte[] bytes=mvc.perform(post("/api/pesquisas/exportacoes/pdf").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try(var pdf=org.apache.pdfbox.Loader.loadPDF(bytes)) {
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf))
                    .contains("Similaridade vetorial", "Mais semelhantes primeiro", "caso.pdf", "Pesquisa baseada no documento anexado");
        }
    }
}
