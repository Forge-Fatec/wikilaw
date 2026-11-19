package forge.wikilaw.backend.controller;

import com.sun.net.httpserver.HttpServer;
import forge.wikilaw.backend.entity.*;
import forge.wikilaw.backend.integration.documents.*;
import forge.wikilaw.backend.repository.*;
import forge.wikilaw.backend.service.DocumentRecordProcessor;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sprint 3: MockMvc -> segurança -> services -> repositories/H2 -> cliente HTTP Gemini real.
 * Somente a API externa de IA é substituída por servidor HTTP local controlado.
 * Não comprova qualidade jurídica do modelo; comprova contexto, contratos e referências.
 * Não usa chaves reais ou .env, nem simula services ou repositories.
 * US22 (perguntas sobre análise) e US28 (temas) não possuem implementação atual.
 * US23 (pesquisas salvas no navegador) e US27 (limpar filtros) são cobertas no Playwright.
 */
@SpringBootTest(properties = {
    "wikilaw.vector.enabled=false", "wikilaw.ai.gemini.enabled=true",
    "wikilaw.ai.gemini.api-key=chave-ficticia-sprint3", "wikilaw.ai.gemini.model=modelo-teste",
    "wikilaw.ai.gemini.max-retries=0"
})
@AutoConfigureMockMvc
@Transactional
class Sprint3BDDIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DocumentRecordProcessor processor;
    @Autowired FonteDadosRepository fontes;
    @Autowired RegistroBrutoRepository registros;
    @Autowired TribunalRepository tribunais;
    private MockHttpSession sessao;
    private Long decisaoId;
    private Long outraDecisaoId;
    private static final AtomicInteger chamadas = new AtomicInteger();
    private static final AtomicInteger statusIa = new AtomicInteger(200);
    private static final AtomicReference<String> requisicaoIa = new AtomicReference<>("");
    private static final AtomicReference<String> respostaIa = new AtomicReference<>(respostaPadrao());
    private static final HttpServer servidor = iniciarServidor();

    @DynamicPropertySource
    static void configurarIa(DynamicPropertyRegistry registry) {
        registry.add("wikilaw.ai.gemini.base-url", Sprint3BDDIntegrationTest::urlIa);
    }

    static String urlIa() { return "http://127.0.0.1:" + servidor.getAddress().getPort(); }

    @AfterAll static void encerrarServidorIa() { servidor.stop(0); }

    @BeforeEach
    void givenPesquisaComDocumentosReaisEUsuarioAutenticado() throws Exception {
        statusIa.set(200); chamadas.set(0); requisicaoIa.set(""); respostaIa.set(respostaPadrao());
        prepararDocumentos();
        sessao = autenticar("Admin", "Admin 123");
    }

    void prepararDocumentos() {
        if (tribunais.findBySigla("TJDFT").isEmpty()) {
            var tribunal = new Tribunal(); tribunal.setSigla("TJDFT"); tribunal.setNome("Tribunal de Justiça do DF");
            tribunais.saveAndFlush(tribunal);
        }
        decisaoId = importar("s3-consumo", "sprinttres dano moral por negativação",
            "Consumidor indevidamente inscrito. CDC, art. 14. Reparação por dano moral.", 2025);
        outraDecisaoId = importar("s3-contrato", "sprinttres dano moral contratual",
            "Responsabilidade civil por cobrança indevida em contrato.", 2024);
        importar("s3-fora", "DOCUMENTO_FORA_DA_PESQUISA", "Concurso público e edital.", 2023);
    }

    @Test @DisplayName("US19: análise concluída retorna texto legível, identificação de IA e resultado associado")
    void deveGerarAnaliseAssociadaAoCaso() throws Exception {
        analisar(corpoAnalise(decisaoId)).andExpect(status().isOk())
            .andExpect(jsonPath("$.resumo", containsString("consumidor")))
            .andExpect(jsonPath("$.geradaPorIa").value(true))
            .andExpect(jsonPath("$.geradaEm").isNotEmpty())
            .andExpect(jsonPath("$.modelo").value("modelo-teste"))
            .andExpect(jsonPath("$.documentosAnalisados").value(1))
            .andExpect(jsonPath("$.resultadosUtilizados[0].id").value(decisaoId.intValue()));
        assertThat(chamadas.get()).isEqualTo(1);
    }

    @Test @DisplayName("US19/US20: sem resultados suficientes, informa falta de contexto e não chama IA")
    void deveRecusarAnaliseSemContexto() throws Exception {
        analisar("{\"termo\":\"\",\"resultados\":[]}")
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").isNotEmpty())
            .andExpect(jsonPath("$.resumo").doesNotExist());
        assertThat(chamadas.get()).isZero();
    }

    @Test @DisplayName("US20: o contexto enviado à IA contém o caso e apenas documentos selecionados do banco")
    void deveContextualizarComResultadosDaPesquisa() throws Exception {
        analisar(corpoAnalise(decisaoId)).andExpect(status().isOk());
        var enviado = json.readTree(requisicaoIa.get());
        String prompt = enviado.get("contents").get(0).get("parts").get(0).get("text").asString();
        assertThat(prompt).contains("sprinttres dano moral", "Consumidor indevidamente inscrito", "CDC, art. 14", "TJDFT")
            .doesNotContain("DOCUMENTO_FORA_DA_PESQUISA", "Responsabilidade civil por cobrança");
        assertThat(enviado.get("generationConfig").get("responseMimeType").asString()).isEqualTo("application/json");
    }

    @Test @DisplayName("US20: documento retirado do acervo não pode fundamentar análise")
    void deveRecusarDocumentoIndisponivel() throws Exception {
        processor.indisponibilizar(DocumentSource.TJDFT, fontes.findBySigla("TJDFT").orElseThrow().getId(), "s3-consumo");
        analisar(corpoAnalise(decisaoId)).andExpect(status().isConflict());
        assertThat(chamadas.get()).isZero();
    }

    @Test @DisplayName("US21: citação identifica o documento jurídico e sua fonte original")
    void deveApresentarFonteCorrespondenteAoDocumentoUtilizado() throws Exception {
        analisar(corpoAnalise(decisaoId)).andExpect(status().isOk())
            .andExpect(jsonPath("$.fontes", hasSize(1)))
            .andExpect(jsonPath("$.fontes[0].numero").value(1))
            .andExpect(jsonPath("$.fontes[0].id").value(decisaoId.intValue()))
            .andExpect(jsonPath("$.fontes[0].titulo").value("sprinttres dano moral por negativação"))
            .andExpect(jsonPath("$.fontes[0].fonte").value("TJDFT"))
            .andExpect(jsonPath("$.fontes[0].identificadorExterno").value("s3-consumo"))
            .andExpect(jsonPath("$.fontes[0].url").value("https://www.tjdft.jus.br/"))
            .andExpect(jsonPath("$.fontes[0].secoes", hasItem("Resumo")));
    }

    @Test @DisplayName("US21: citações a documentos inexistentes são descartadas")
    void deveDescartarFonteInventadaPeloProvedor() throws Exception {
        respostaIa.set(respostaPadrao().replace("[1]", "[999]"));
        analisar(corpoAnalise(decisaoId)).andExpect(status().isOk())
            .andExpect(jsonPath("$.resumo", not(containsString("999"))))
            .andExpect(jsonPath("$.fontes", empty()));
    }

    @Test @DisplayName("US21: análise sem citações verificáveis retorna fontes vazias")
    void deveRetornarFontesVaziasSemInventarReferencias() throws Exception {
        respostaIa.set(respostaPadrao().replace(" [1]", ""));
        analisar(corpoAnalise(decisaoId)).andExpect(status().isOk())
            .andExpect(jsonPath("$.fontes", empty())).andExpect(jsonPath("$.resumo").isNotEmpty());
    }

    @Test @DisplayName("US19: indisponibilidade do provedor não é apresentada como análise concluída")
    void deveInformarFalhaDoProvedor() throws Exception {
        statusIa.set(503);
        analisar(corpoAnalise(decisaoId)).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error").isNotEmpty()).andExpect(jsonPath("$.resumo").doesNotExist());
    }

    @Test @DisplayName("US19: resposta inválida da IA não é apresentada como análise concluída")
    void deveRecusarRespostaInvalidaDaIa() throws Exception {
        respostaIa.set("{\"resumo\":\"\"}");
        analisar(corpoAnalise(decisaoId)).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.resumo").doesNotExist());
    }

    @Test @DisplayName("US24: dashboard consolidado representa somente os resultados selecionados")
    void deveConsolidarDashboardDoCaso() throws Exception {
        mvc.perform(post("/api/dashboard/resultados").session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("idsDecisoes", List.of(decisaoId, outraDecisaoId)))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.indicadores.totalDecisoes").value(2))
            .andExpect(jsonPath("$.indicadores.aderencia").value(100))
            .andExpect(jsonPath("$.evolucaoAnual", hasSize(2)));
    }

    @Test @DisplayName("US24: dashboard sem resultados retorna estado vazio")
    void deveRetornarDashboardVazio() throws Exception {
        mvc.perform(get("/api/dashboard").session(sessao).param("termo", "termoausentesprinttres"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.indicadores.totalDecisoes").value(0))
            .andExpect(jsonPath("$.indicadores.aderencia").value(nullValue()));
    }

    @Test @DisplayName("US25: pesquisa combina termo, fonte, tribunal e período")
    void devePesquisarComCriteriosCombinados() throws Exception {
        mvc.perform(get("/api/documentos/decisoes").session(sessao).param("termo", "sprinttres")
                .param("fonte", "TJDFT").param("tribunal", "TJDFT")
                .param("dataDe", "2025-01-01").param("dataAte", "2025-12-31"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.itens[0].id").value(decisaoId.intValue()));
    }

    @Test @DisplayName("US25: pesquisa sem correspondências retorna lista vazia sem erro")
    void devePesquisarSemCorrespondencias() throws Exception {
        mvc.perform(get("/api/documentos/decisoes").session(sessao).param("termo", "termoausentesprinttres"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.itens", empty()));
    }

    @Test @DisplayName("US26: usuário autenticado consulta apenas sua identificação sem senha")
    void deveConsultarPropriaConta() throws Exception {
        MockHttpSession usuario = criarPesquisadorEAutenticar();
        mvc.perform(get("/api/auth/me").session(usuario)).andExpect(status().isOk())
            .andExpect(jsonPath("$.login").value("pesquisador-sprint3"))
            .andExpect(jsonPath("$.perfil").value("USER"))
            .andExpect(jsonPath("$.senha").doesNotExist()).andExpect(jsonPath("$.senhaHash").doesNotExist());
    }

    @Test @DisplayName("US26: pesquisador não pode consultar contas de outros usuários")
    void deveNegarDadosDeOutrasContas() throws Exception {
        mvc.perform(get("/api/admin/usuarios").session(criarPesquisadorEAutenticar()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$[*].login").doesNotExist());
    }

    @Test @DisplayName("US26: acesso sem autenticação retorna 401 sem dados protegidos")
    void deveNegarAcessoSemAutenticacao() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.login").doesNotExist());
        mvc.perform(get("/api/documentos/decisoes")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.itens").doesNotExist());
    }

    @Test @DisplayName("US23: marco do acompanhamento corresponde ao acervo real")
    void deveConsultarMarcoDoAcervoParaAcompanhamento() throws Exception {
        mvc.perform(get("/api/acompanhamentos/marco").session(sessao))
            .andExpect(status().isOk()).andExpect(jsonPath("$.decisoes", greaterThanOrEqualTo(outraDecisaoId.intValue())))
            .andExpect(jsonPath("$.precedentes").isNumber()).andExpect(jsonPath("$.doutrina").isNumber());
    }

    private MockHttpSession criarPesquisadorEAutenticar() throws Exception {
        mvc.perform(post("/api/admin/usuarios").session(sessao).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"pesquisador-sprint3\",\"senha\":\"Senha teste 123\",\"perfil\":\"USER\"}"))
            .andExpect(status().isCreated());
        return autenticar("pesquisador-sprint3", "Senha teste 123");
    }

    private MockHttpSession autenticar(String login, String senha) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("login", login, "senha", senha))))
            .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private ResultActions analisar(String body) throws Exception {
        return mvc.perform(post("/api/pesquisas/analises").session(sessao).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String corpoAnalise(Long id) throws Exception {
        return json.writeValueAsString(Map.of("termo", "sprinttres dano moral", "resultados", List.of(Map.of("categoria", "decisoes", "id", id))));
    }

    private Long importar(String identificador, String titulo, String ementa, int ano) {
        var fonte = fontes.findBySigla("TJDFT").orElseGet(() -> {
            var nova = new FonteDados(); nova.setSigla("TJDFT"); nova.setNome("TJDFT"); nova.setTipoFonte("TESTE");
            return fontes.saveAndFlush(nova);
        });
        var bruto = new RegistroBruto(); bruto.setFonte(fonte); bruto.setFormatoPayload("JSON");
        bruto.setPayloadTexto("{}"); bruto.setDataColeta(OffsetDateTime.now());
        bruto.setHashConteudo(UUID.randomUUID().toString().replace("-", "") + "c".repeat(32));
        bruto = registros.saveAndFlush(bruto);
        return processor.process(DocumentSource.TJDFT, fonte.getId(), bruto.getId(), NormalizedDocument.builder()
            .identificador(identificador).titulo(titulo).resumo(ementa).decisao("Recurso provido.")
            .tribunal("TJDFT").orgaoJulgador("Primeira Turma").dataJulgamento(LocalDate.of(ano, 3, 10))
            .url("https://www.tjdft.jus.br/").metadados("{}").build());
    }

    private static String respostaPadrao() {
        return """
            {"resumo":"A proteção do consumidor fundamenta a reparação [1].",
             "questaoJuridica":"Responsabilidade por negativação indevida.",
             "entendimentoPredominante":"Reparação pelo dano moral.",
             "pontosPrincipais":[],"fundamentosLegais":[],"divergencias":[],
             "documentosRelevantes":[],"limitacoes":[]}
            """;
    }

    private static HttpServer iniciarServidor() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1beta/models/modelo-teste:generateContent", exchange -> {
                try {
                    chamadas.incrementAndGet();
                    requisicaoIa.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    var mapper = new ObjectMapper();
                    String body = mapper.writeValueAsString(Map.of("candidates", List.of(Map.of("finishReason", "STOP",
                        "content", Map.of("parts", List.of(Map.of("text", respostaIa.get())))))));
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                    exchange.sendResponseHeaders(statusIa.get(), bytes.length);
                    exchange.getResponseBody().write(bytes);
                } finally { exchange.close(); }
            });
            server.start(); return server;
        } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
    }
}
