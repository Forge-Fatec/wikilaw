package forge.wikilaw.backend.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import forge.wikilaw.backend.entity.*;
import forge.wikilaw.backend.integration.datajud.*;
import forge.wikilaw.backend.integration.datajud.dto.DataJudImportRequest;
import forge.wikilaw.backend.integration.documents.*;
import forge.wikilaw.backend.repository.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import javax.sql.DataSource;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:wikilaw_jobs;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON")
@AutoConfigureMockMvc(addFilters = false)
class ImportJobIntegrationTest {
    @DynamicPropertySource
    static void optionalPostgres(DynamicPropertyRegistry properties) {
        String url = System.getProperty("wikilaw.test.postgres-url");
        if (url == null) return;
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> System.getProperty("wikilaw.test.postgres-user", "wikilaw_test"));
        properties.add("spring.datasource.password", () -> System.getProperty("wikilaw.test.postgres-password", ""));
        properties.add("spring.flyway.enabled", () -> "true");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired DataSource database;
    @Autowired ImportJobService service;
    @Autowired ImportJobProcessor processor;
    @Autowired ImportJobRepository jobs;
    @Autowired FonteDadosRepository sources;
    @Autowired ProcessoRepository processes;
    @Autowired ProcessoInstanciaRepository instances;
    @Autowired MovimentoProcessualRepository movements;
    @Autowired DecisaoJudicialRepository decisions;
    @Autowired TribunalRepository tribunals;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @MockitoBean DataJudClient client;
    @MockitoBean PublicSourceHttpClient documentsHttp;
    private String fixture;
    private static final String EMPTY = "{\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"},\"hits\":[]}}";

    @BeforeEach
    void setup() throws Exception {
        jobs.deleteAll();
        try (var stream = getClass().getResourceAsStream("/fixtures/datajud-response.json")) {
            fixture = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        seed("DATAJUD"); seed("STJ");
        if (tribunals.findBySigla("STJ").isEmpty()) {
            var t = new Tribunal(); t.setSigla("STJ"); t.setNome("STJ"); t.setRamoJustica("SUPERIOR");
            tribunals.save(t);
        }
    }

    private void seed(String name) {
        if (sources.findBySigla(name).isPresent()) return;
        var f = new FonteDados(); f.setSigla(name); f.setNome(name); f.setTipoFonte("TESTE"); f.setAtivo(true);
        sources.save(f);
    }

    @Test
    void httpAcceptsImmediatelyAndReportsPersistedProgress() throws Exception {
        var response = mvc.perform(post("/api/integrations/datajud/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tribunal\":\"TJSP\",\"tamanhoPagina\":1,\"maximoPaginas\":10}"))
                .andExpect(status().isAccepted()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("PENDENTE")).andReturn();
        verifyNoInteractions(client);
        long id = json.readTree(response.getResponse().getContentAsString()).path("idTarefa").asLong();
        when(client.search(eq(DataJudTribunal.TJSP), anyString()))
                .thenReturn(new DataJudRawResponse(fixture), new DataJudRawResponse(EMPTY));
        run(id);
        mvc.perform(get("/api/integrations/tarefas/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.paginas").value(1)).andExpect(jsonPath("$.processados").value(1));
        run(id);
        assertThat(service.get(id).status()).isEqualTo(ImportJob.Status.CONCLUIDA);
        verify(client).search(eq(DataJudTribunal.TJSP), argThat(query -> query.contains("search_after")));
    }

    @Test
    void documentEndpointQueuesPaginationAndRejectsUnsupportedFilters() throws Exception {
        mvc.perform(post("/api/integrations/documentos/STJ/import?maximoPaginas=50")
                .contentType(MediaType.APPLICATION_JSON).content("{\"offset\":0,\"tamanhoPagina\":100}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.maximoPaginas").value(50));
        verifyNoInteractions(documentsHttp);
        mvc.perform(post("/api/integrations/documentos/TJDFT/import")
                .contentType(MediaType.APPLICATION_JSON).content("{\"offset\":20}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fatalFailureKeepsCheckpointAndCanResumeAfterRecreatingWorker() {
        var job = service.enqueueDataJud(new DataJudImportRequest(DataJudTribunal.TJSP, null, 1, 10));
        when(client.search(eq(DataJudTribunal.TJSP), anyString()))
                .thenReturn(new DataJudRawResponse(fixture)).thenThrow(new IllegalStateException("Timeout"))
                .thenReturn(new DataJudRawResponse(EMPTY));
        run(job.idTarefa());
        String cursor = jobs.findById(job.idTarefa()).orElseThrow().getCursorJson();
        run(job.idTarefa());
        assertThat(service.get(job.idTarefa()).status()).isEqualTo(ImportJob.Status.FALHA);
        assertThat(service.get(job.idTarefa()).paginas()).isEqualTo(1);
        assertThat(jobs.findById(job.idTarefa()).orElseThrow().getCursorJson()).isEqualTo(cursor);
        service.resume(job.idTarefa(), 10);
        new ImportJobWorker(processor).poll();
        assertThat(service.get(job.idTarefa()).status()).isEqualTo(ImportJob.Status.CONCLUIDA);
        assertThat(service.get(job.idTarefa()).processados()).isEqualTo(1);
    }

    @Test
    void replayAfterLostCheckpointDoesNotDuplicateBusinessRecords() {
        var job = service.enqueueDataJud(new DataJudImportRequest(DataJudTribunal.TJSP, null, 1, 10));
        when(client.search(eq(DataJudTribunal.TJSP), anyString())).thenReturn(new DataJudRawResponse(fixture));
        processor.prepareNext();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            processor.processPage(job.idTarefa()); tx.setRollbackOnly();
        });
        assertThat(service.get(job.idTarefa()).paginas()).isZero();
        long processCount = processes.count(), instanceCount = instances.count(), movementCount = movements.count();
        run(job.idTarefa());
        assertThat(service.get(job.idTarefa()).paginas()).isEqualTo(1);
        assertThat(processes.count()).isEqualTo(processCount);
        assertThat(instances.count()).isEqualTo(instanceCount);
        assertThat(movements.count()).isEqualTo(movementCount);
    }

    @Test
    void rowLockMakesCompetingWorkerSkipBusyJob() throws Exception {
        var first = service.enqueueDataJud(new DataJudImportRequest(DataJudTribunal.TJSP, null, 10, 1));
        var second = service.enqueueDataJud(new DataJudImportRequest(DataJudTribunal.TJMG, null, 10, 1));
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jobs.lockById(first.idTarefa()).orElseThrow(); locked.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Lock timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            try {
                assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(processor.prepareNext()).contains(second.idTarefa());
            } finally { release.countDown(); }
            future.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void limitCanBeExtendedAndInvalidRequestsNeverReachExternalApi() throws Exception {
        var job = service.enqueueDataJud(new DataJudImportRequest(DataJudTribunal.TJSP, null, 1, 1));
        when(client.search(eq(DataJudTribunal.TJSP), anyString()))
                .thenReturn(new DataJudRawResponse(fixture), new DataJudRawResponse(EMPTY));
        run(job.idTarefa());
        assertThat(service.get(job.idTarefa()).status()).isEqualTo(ImportJob.Status.LIMITE_ATINGIDO);
        service.resume(job.idTarefa(), 2); run(job.idTarefa());
        assertThat(service.get(job.idTarefa()).status()).isEqualTo(ImportJob.Status.CONCLUIDA);
        mvc.perform(post("/api/integrations/datajud/import").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tribunal\":\"TJSP\",\"tamanhoPagina\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/integrations/tarefas/9999999")).andExpect(status().isNotFound());
    }

    @Test
    void stjDownloadsDatasetOnlyOnceAcrossPagesAndKeepsAuditReferences() {
        String catalogUrl = "https://dadosabertos.web.stj.jus.br/api/3/action/package_show?id=espelhos-de-acordaos-corte-especial";
        String download = "https://dadosabertos.web.stj.jus.br/test.json";
        when(documentsHttp.request(catalogUrl, null)).thenReturn(new PublicSourceHttpClient.Response(200,
                "{\"success\":true,\"result\":{\"resources\":[{\"id\":\"resource\",\"name\":\"2026\",\"format\":\"JSON\",\"url\":\"" + download + "\"}]}}", "application/json"));
        when(documentsHttp.request(download, null)).thenReturn(new PublicSourceHttpClient.Response(200,
                "[{\"id\":\"cache-1\",\"siglaClasse\":\"REsp\",\"numeroProcesso\":\"123\"},{\"id\":\"cache-2\",\"siglaClasse\":\"REsp\",\"numeroProcesso\":\"456\"}]", "application/json"));
        var request = new DocumentImportRequest(null, null, 1, 0, null, null, null, null, null, null, null);
        var job = service.enqueueDocuments(DocumentSource.STJ, request, 10);
        run(job.idTarefa());
        Long rawId = decisions.findByIdFonteAndIdentificadorExterno(sources.findBySigla("STJ").orElseThrow().getId(), "cache-1")
                .orElseThrow().getIdRegistroBruto();
        run(job.idTarefa());
        assertThat(service.get(job.idTarefa()).status()).isEqualTo(ImportJob.Status.CONCLUIDA);
        assertThat(service.get(job.idTarefa()).processados()).isEqualTo(2);
        assertThat(decisions.findByIdFonteAndIdentificadorExterno(sources.findBySigla("STJ").orElseThrow().getId(), "cache-2")
                .orElseThrow().getIdRegistroBruto()).isEqualTo(rawId);
        verify(documentsHttp, times(1)).request(download, null);
        verify(documentsHttp, times(1)).request(catalogUrl, null);
    }

    private void run(Long id) {
        assertThat(processor.prepareNext()).contains(id); processor.processPage(id);
    }

    @Test
    void incrementalUsesOnlyFullyCompletedUnfilteredLoadsAsWatermark() {
        when(client.search(eq(DataJudTribunal.TJSP), anyString())).thenReturn(new DataJudRawResponse(EMPTY));
        var first = service.enqueueDataJud(new DataJudImportRequest(DataJudTribunal.TJSP, null, 10, 10), true);
        var firstRequest = json.readValue(jobs.findById(first.idTarefa()).orElseThrow().getRequisicao(), DataJudImportRequest.class);
        assertThat(firstRequest.desde()).isNull();
        run(first.idTarefa());
        // A filtered search must never advance the watermark for the full tribunal.
        var filtered = service.enqueueDataJud(new DataJudImportRequest(DataJudTribunal.TJSP, "40017037420268260360", 10, 10));
        run(filtered.idTarefa());
        var next = service.enqueueDataJud(new DataJudImportRequest(DataJudTribunal.TJSP, null, 10, 10), true);
        var nextRequest = json.readValue(jobs.findById(next.idTarefa()).orElseThrow().getRequisicao(), DataJudImportRequest.class);
        assertThat(nextRequest.desde()).isEqualTo(firstRequest.ate().minusMinutes(5));
        service.cancel(next.idTarefa());
        assertThat(service.get(next.idTarefa()).status()).isEqualTo(ImportJob.Status.CANCELADA);
        assertThatThrownBy(() -> service.resume(next.idTarefa(), 10)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void postgresUniqueIndexPreventsCompetingSnapshotsAndCancelFreesScope() throws Exception {
        try (var connection = database.getConnection()) {
            Assumptions.assumeTrue("PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()));
        }
        var request = new DataJudImportRequest(DataJudTribunal.TJSP, null, 10, 10);
        var first = service.enqueueDataJud(request);
        assertThatThrownBy(() -> service.enqueueDataJud(request))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("409");
        service.cancel(first.idTarefa());
        assertThat(service.enqueueDataJud(request).idTarefa()).isNotEqualTo(first.idTarefa());
    }

    @Test
    void fullHarvestRunsBeyondThreePagesAndBootstrapReusesItsCheckpoint() {
        seed("TJDFT");
        if (tribunals.findBySigla("TJDFT").isEmpty()) {
            var tribunal = new Tribunal(); tribunal.setSigla("TJDFT"); tribunal.setNome("TJDFT");
            tribunal.setRamoJustica("ESTADUAL"); tribunals.save(tribunal);
        }
        when(documentsHttp.request(eq("https://jurisdf.tjdft.jus.br/api/v1/pesquisa"),anyString()))
                .thenAnswer(call -> {
                    var body = json.readTree((String) call.getArgument(1));
                    assertThat(body.path("query").asText()).isEmpty();
                    int page = body.path("pagina").asInt();
                    return new PublicSourceHttpClient.Response(200,"{\"hits\":{\"value\":5},\"registros\":[{\"uuid\":\"full-"
                            +page+"\",\"identificador\":\""+page+"\"}]}","application/json");
                });
        var request = new DocumentImportRequest(null,0,1,null,null,null,null,null,null,null,null).withFullCollection();
        var task = service.ensureDocumentsComplete(DocumentSource.TJDFT,request);
        run(task.idTarefa()); run(task.idTarefa());
        assertThat(service.ensureDocumentsComplete(DocumentSource.TJDFT,request).idTarefa()).isEqualTo(task.idTarefa());
        assertThat(service.get(task.idTarefa()).paginas()).isEqualTo(2);
        run(task.idTarefa());
        assertThat(service.get(task.idTarefa()).status()).isEqualTo(ImportJob.Status.EM_EXECUCAO);
        run(task.idTarefa()); run(task.idTarefa());
        assertThat(service.get(task.idTarefa()).status()).isEqualTo(ImportJob.Status.CONCLUIDA);
        assertThat(service.get(task.idTarefa()).paginas()).isEqualTo(5);
        assertThat(service.get(task.idTarefa()).processados()).isEqualTo(5);
        assertThat(service.ensureDocumentsComplete(DocumentSource.TJDFT,request).idTarefa()).isEqualTo(task.idTarefa());
        assertThat(jobs.count()).isEqualTo(1);
    }

    @Test
    void fullBootstrapReplacesLegacySampleWithoutKeepingItsTermOrCursor() {
        var legacyRequest = new DocumentImportRequest("dano moral",0,20,null,null,null,null,null,null,null,null);
        var old = service.enqueueDocuments(DocumentSource.TJDFT,legacyRequest,3);
        var entity = jobs.findById(old.idTarefa()).orElseThrow();
        entity.setStatus(ImportJob.Status.LIMITE_ATINGIDO); entity.setPaginas(3); jobs.save(entity);
        var full = service.ensureDocumentsComplete(DocumentSource.TJDFT,
                new DocumentImportRequest(null,0,20,null,null,null,null,null,null,null,null));
        assertThat(service.get(old.idTarefa()).status()).isEqualTo(ImportJob.Status.CANCELADA);
        assertThat(full.ateEsgotar()).isTrue();
        var request = json.readValue(jobs.findById(full.idTarefa()).orElseThrow().getRequisicao(),DocumentImportRequest.class);
        assertThat(request.query()).isEmpty(); assertThat(request.page()).isZero();
    }
}
