package forge.wikilaw.backend.controller;

import forge.wikilaw.backend.dto.PesquisaPdfRequest;
import forge.wikilaw.backend.dto.PesquisaPdfRequest.DocumentoReferencia;
import forge.wikilaw.backend.entity.*;
import forge.wikilaw.backend.integration.documents.*;
import forge.wikilaw.backend.repository.*;
import forge.wikilaw.backend.service.DocumentRecordProcessor;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "wikilaw.vector.enabled=false")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class Sprint2BDDIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DocumentRecordProcessor processor;
    @Autowired FonteDadosRepository fontes;
    @Autowired RegistroBrutoRepository registros;
    @Autowired TribunalRepository tribunais;
    private Long primeiraDecisao;
    private static final String CASO = "Consumidor sofreu negativacao indevida. Pede indenizacao por dano moral.";
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @BeforeEach
    void givenPesquisaComDecisoesDeDiferentesResultadosTurmasEAnos() {
        if (tribunais.findBySigla("TJDFT").isEmpty()) {
            var tribunal = new Tribunal();
            tribunal.setSigla("TJDFT"); tribunal.setNome("Tribunal de Justiça do DF");
            tribunais.saveAndFlush(tribunal);
        }
        primeiraDecisao = importar("s2-provida-2024", "sprintdois consumidor", "Recurso provido.", "Primeira Turma", 2024);
        importar("s2-negada-2025", "sprintdois consumidor", "Recurso desprovido.", "Segunda Turma", 2025);
        importar("s2-provida-2025", "sprintdois consumidor", "Recurso provido.", "Segunda Turma", 2025);
        importar("s2-panorama", "Concurso público", "Recurso desprovido.", "Terceira Turma", 2023);
        importar("s2-inativa", "sprintdois indisponível", "Recurso provido.", "Primeira Turma", 2025);
        processor.indisponibilizar(DocumentSource.TJDFT, fontes.findBySigla("TJDFT").orElseThrow().getId(), "s2-inativa");
    }

    @Test @DisplayName("US11: pesquisa ativa apresenta total e percentual de aderência")
    void deveApresentarIndicadoresDaPesquisa() throws Exception {
        mvc.perform(get("/api/dashboard").param("termo", "sprintdois"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.indicadores.totalDecisoes").value(3))
            .andExpect(jsonPath("$.indicadores.classificadas").value(3))
            .andExpect(jsonPath("$.indicadores.favoraveis").value(2))
            .andExpect(jsonPath("$.indicadores.aderencia").value(67));
    }

    @Test @DisplayName("US11: distribuição de mérito contém os resultados da pesquisa")
    void deveDistribuirMerito() throws Exception {
        mvc.perform(get("/api/dashboard/distribuicao-merito").param("termo", "sprintdois"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.categoria == 'Provido')].quantidade").value(contains(2)))
            .andExpect(jsonPath("$[?(@.categoria == 'Desprovido')].quantidade").value(contains(1)));
    }

    @Test @DisplayName("US11: distribuição por turmas e evolução temporal respeitam a pesquisa")
    void deveDistribuirTurmasEAnos() throws Exception {
        mvc.perform(get("/api/dashboard/distribuicao-camara-turma").param("termo", "sprintdois"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.categoria == 'Primeira Turma')].quantidade").value(contains(1)))
            .andExpect(jsonPath("$[?(@.categoria == 'Segunda Turma')].quantidade").value(contains(2)));
        mvc.perform(get("/api/dashboard/evolucao-anual").param("termo", "sprintdois"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.categoria == '2024')].quantidade").value(contains(1)))
            .andExpect(jsonPath("$[?(@.categoria == '2025')].quantidade").value(contains(2)));
    }

    @Test @DisplayName("US12: seleção no gráfico atualiza indicadores e demais distribuições")
    void deveAplicarSelecaoDoGrafico() throws Exception {
        mvc.perform(get("/api/dashboard").param("termo", "sprintdois").param("resultado", "Provido"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.indicadores.totalDecisoes").value(2))
            .andExpect(jsonPath("$.indicadores.aderencia").value(100))
            .andExpect(jsonPath("$.distribuicaoCamaraTurma[*].quantidade").value(contains(1, 1)));
    }

    @Test @DisplayName("US12: filtros combinados de mérito e turma consideram a interseção")
    void deveCombinarSelecoes() throws Exception {
        mvc.perform(get("/api/dashboard").param("termo", "sprintdois")
                .param("resultado", "Provido").param("orgaoJulgador", "Segunda Turma"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.indicadores.totalDecisoes").value(1))
            .andExpect(jsonPath("$.indicadores.favoraveis").value(1))
            .andExpect(jsonPath("$.evolucaoAnual[0].categoria").value("2025"));
    }

    @Test @DisplayName("US12: remover filtro restaura indicadores e distribuições da pesquisa")
    void deveRestaurarPesquisaAoRemoverSelecao() throws Exception {
        String antes = dashboard("sprintdois");
        mvc.perform(get("/api/dashboard").param("termo", "sprintdois").param("resultado", "Desprovido"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.indicadores.totalDecisoes").value(1));
        assertThat(json.readTree(dashboard("sprintdois"))).isEqualTo(json.readTree(antes));
    }

    @Test @DisplayName("US13: panorama do tribunal amplia o escopo e permite retornar à pesquisa")
    void deveAlternarEntrePesquisaEPanoramaDoTribunal() throws Exception {
        String pesquisa = dashboard("sprintdois");
        mvc.perform(get("/api/dashboard").param("tribunal", "TJDFT"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.indicadores.totalDecisoes").value(4))
            .andExpect(jsonPath("$.indicadores.aderencia").value(50));
        assertThat(json.readTree(dashboard("sprintdois"))).isEqualTo(json.readTree(pesquisa));
    }

    @Test @DisplayName("US11: pesquisa sem resultados apresenta total zero sem percentual inventado")
    void deveApresentarDashboardVazio() throws Exception {
        mvc.perform(get("/api/dashboard").param("termo", "termoausentesprintdois"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.indicadores.totalDecisoes").value(0))
            .andExpect(jsonPath("$.indicadores.aderencia").value(nullValue()));
    }

    @Test @DisplayName("US16: PDF válido retorna texto extraído para contextualizar a pesquisa")
    void deveProcessarPdf() throws Exception {
        var resposta = enviar("caso.pdf", "application/pdf", pdf());
        assertThat(resposta.get("formato").asString()).isEqualTo("PDF");
        assertThat(resposta.get("texto").asString()).contains("negativacao indevida", "dano moral");
        assertThat(resposta.get("paginas").asInt()).isEqualTo(1);
    }

    @Test @DisplayName("US16: DOCX válido retorna o conteúdo do documento")
    void deveProcessarDocx() throws Exception {
        var resposta = enviar("caso.docx", DOCX, docx());
        assertThat(resposta.get("formato").asString()).isEqualTo("DOCX");
        assertThat(resposta.get("texto").asString()).contains(CASO);
    }

    @Test @DisplayName("US16: formato não suportado é recusado sem processamento")
    void deveRejeitarFormatoNaoSuportado() throws Exception {
        mvc.perform(multipart("/api/pesquisas/documentos")
                .file(new MockMultipartFile("arquivo", "caso.txt", "text/plain", CASO.getBytes(StandardCharsets.UTF_8))))
            .andExpect(status().isUnsupportedMediaType());
    }

    @Test @DisplayName("US16: arquivo disfarçado de PDF não é aceito")
    void deveRejeitarPdfDisfarcado() throws Exception {
        mvc.perform(multipart("/api/pesquisas/documentos")
                .file(new MockMultipartFile("arquivo", "caso.pdf", "application/pdf", CASO.getBytes(StandardCharsets.UTF_8))))
            .andExpect(status().isUnsupportedMediaType());
    }

    @Test @DisplayName("US16: texto extraído pode complementar o contexto textual da pesquisa")
    void devePrepararContextoDoDocumentoEnviado() throws Exception {
        var resposta = enviar("caso.docx", DOCX, docx());
        mvc.perform(post("/api/pesquisas/contexto").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("termo", "indenizacao", "texto", resposta.get("texto").asString()))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.consulta", containsString("negativacao")))
            .andExpect(jsonPath("$.termosContexto", hasItem("consumidor")));
    }

    @Test @DisplayName("US18: exportação disponibiliza PDF com os dados e resultados selecionados")
    void deveExportarPesquisaComConteudoEArquivoParaDownload() throws Exception {
        var request = new PesquisaPdfRequest("sprintdois", List.of("decisoes"), "TJDFT", "TJDFT",
            null, null, "mais-recentes", List.of(new DocumentoReferencia("decisoes", primeiraDecisao)));
        var response = mvc.perform(post("/api/pesquisas/exportacoes/pdf").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)))
            .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string("Content-Disposition", containsString("attachment; filename=")))
            .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
        byte[] bytes = response.getContentAsByteArray();
        assertThat(new String(bytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (var documento = Loader.loadPDF(bytes)) {
            assertThat(documento.getNumberOfPages()).isPositive();
            assertThat(new PDFTextStripper().getText(documento))
                .contains("sprintdois consumidor", "Recurso provido", "TJDFT", "Dados da pesquisa")
                .doesNotContain("Concurso público", "sprintdois indisponível");
        }
    }

    @Test @DisplayName("US18: exportação sem resultados é recusada")
    void deveRejeitarExportacaoVazia() throws Exception {
        mvc.perform(post("/api/pesquisas/exportacoes/pdf").contentType(MediaType.APPLICATION_JSON)
                .content("{\"termo\":\"sprintdois\",\"categorias\":[\"decisoes\"],\"ordenacao\":\"mais-recentes\",\"resultados\":[]}"))
            .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("US17: apresentar fatos e teses identificados por IA antes da pesquisa")
    @Disabled("O contrato atual de upload retorna texto, sem fatos/teses nem endpoint de extração por IA; requer implementação da US17.")
    void deveApresentarFatosETesesAntesDaBusca() {
        Assertions.fail("Implementar a verificação do contrato de fatos/teses quando a aplicação o disponibilizar.");
    }

    private String dashboard(String termo) throws Exception {
        return mvc.perform(get("/api/dashboard").param("termo", termo)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private tools.jackson.databind.JsonNode enviar(String nome, String tipo, byte[] bytes) throws Exception {
        var response = mvc.perform(multipart("/api/pesquisas/documentos")
                .file(new MockMultipartFile("arquivo", nome, tipo, bytes)))
            .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsString(StandardCharsets.UTF_8));
    }

    private byte[] pdf() throws Exception {
        try (var documento = new PDDocument(); var out = new ByteArrayOutputStream()) {
            var pagina = new PDPage(); documento.addPage(pagina);
            try (var stream = new PDPageContentStream(documento, pagina)) {
                stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                stream.newLineAtOffset(40, 700); stream.showText(CASO); stream.endText();
            }
            documento.save(out); return out.toByteArray();
        }
    }

    private byte[] docx() throws Exception {
        try (var documento = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            documento.createParagraph().createRun().setText(CASO);
            documento.write(out); return out.toByteArray();
        }
    }

    private Long importar(String identificador, String titulo, String decisao, String turma, int ano) {
        var fonte = fontes.findBySigla("TJDFT").orElseGet(() -> {
            var nova = new FonteDados(); nova.setSigla("TJDFT"); nova.setNome("TJDFT"); nova.setTipoFonte("TESTE");
            return fontes.saveAndFlush(nova);
        });
        var bruto = new RegistroBruto(); bruto.setFonte(fonte); bruto.setFormatoPayload("JSON");
        bruto.setPayloadTexto("{}"); bruto.setDataColeta(OffsetDateTime.now());
        bruto.setHashConteudo(UUID.randomUUID().toString().replace("-", "") + "a".repeat(32));
        bruto = registros.saveAndFlush(bruto);
        return processor.process(DocumentSource.TJDFT, fonte.getId(), bruto.getId(), NormalizedDocument.builder()
            .identificador(identificador).titulo(titulo).resumo("Ementa: " + titulo).decisao(decisao)
            .orgaoJulgador(turma).dataJulgamento(LocalDate.of(ano, 3, 10)).metadados("{}").build());
    }
}
