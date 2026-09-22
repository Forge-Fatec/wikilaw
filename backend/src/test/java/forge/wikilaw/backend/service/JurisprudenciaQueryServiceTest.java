package forge.wikilaw.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import forge.wikilaw.backend.entity.FonteDados;
import forge.wikilaw.backend.entity.RegistroBruto;
import forge.wikilaw.backend.entity.Tribunal;
import forge.wikilaw.backend.integration.documents.DocumentSource;
import forge.wikilaw.backend.integration.documents.NormalizedDocument;
import forge.wikilaw.backend.repository.FonteDadosRepository;
import forge.wikilaw.backend.repository.RegistroBrutoRepository;
import forge.wikilaw.backend.repository.TribunalRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class JurisprudenciaQueryServiceTest {

    @Autowired
    private JurisprudenciaQueryService service;

    @Autowired
    private DocumentRecordProcessor processor;

    @Autowired
    private FonteDadosRepository fontes;

    @Autowired
    private TribunalRepository tribunais;

    @Autowired
    private RegistroBrutoRepository registrosBrutos;

    @Autowired
    private MockMvc mockMvc;

    private FonteDados fonte;
    private RegistroBruto registroBruto;

    @BeforeEach
    void prepararDados() {
        fonte = new FonteDados();
        fonte.setNome("Jurisprudência TJDFT");
        fonte.setSigla("TJDFT");
        fonte.setTipoFonte("JURISPRUDENCIA");
        fonte = fontes.saveAndFlush(fonte);

        Tribunal tribunal = new Tribunal();
        tribunal.setSigla("TJDFT");
        tribunal.setNome("Tribunal de Justiça do Distrito Federal e dos Territórios");
        tribunal.setUf("DF");
        tribunais.saveAndFlush(tribunal);

        registroBruto = new RegistroBruto();
        registroBruto.setFonte(fonte);
        registroBruto.setFormatoPayload("JSON");
        registroBruto.setDataColeta(OffsetDateTime.now());
        registroBruto.setHashConteudo("b".repeat(64));
        registroBruto.setPayloadTexto("{}");
        registroBruto = registrosBrutos.saveAndFlush(registroBruto);
    }

    @Test
    void buscaPorTermoTribunalEPeriodo() {
        salvarDecisao(
                "decisao-1",
                "Responsabilidade civil do fornecedor",
                "A negativação indevida gera dano moral.",
                LocalDate.of(2025, 5, 10));
        salvarDecisao(
                "decisao-2",
                "Direito administrativo",
                "Discussão sobre concurso público.",
                LocalDate.of(2023, 1, 20));

        var resultado = service.buscar(
                "DANO MORAL",
                "tjdft",
                "tjdft",
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                0,
                20);

        assertEquals(1, resultado.total());
        assertEquals("Responsabilidade civil do fornecedor", resultado.itens().getFirst().titulo());
        assertEquals("TJDFT", resultado.itens().getFirst().fonte());
        assertEquals("TJDFT", resultado.itens().getFirst().tribunal());
        assertEquals("0702497-45.2026.8.07.0007", resultado.itens().getFirst().numeroProcesso());
    }

    @Test
    void retornaResultadoQuandoQualquerPalavraChaveCombina() {
        salvarDecisao(
                "decisao-palavra-chave",
                "Responsabilidade do fornecedor",
                "A negativação foi considerada indevida.",
                LocalDate.of(2025, 6, 1));

        var resultado = service.buscar(
                "tributário inexistente consumidor negativação",
                null,
                null,
                null,
                null,
                0,
                20);

        assertEquals(1, resultado.total());
        assertEquals("Responsabilidade do fornecedor", resultado.itens().getFirst().titulo());
    }

    @Test
    void ignoraDecisaoInativa() {
        salvarDecisao(
                "decisao-inativa",
                "Dano moral",
                "Registro retirado pela fonte.",
                LocalDate.of(2025, 2, 1));
        processor.indisponibilizar(DocumentSource.TJDFT, fonte.getId(), "decisao-inativa");

        var resultado = service.buscar("dano moral", null, null, null, null, 0, 20);

        assertEquals(0, resultado.total());
    }

    @Test
    void rejeitaPeriodoInvertido() {
        assertThrows(
                ResponseStatusException.class,
                () -> service.buscar(
                        null,
                        null,
                        null,
                        LocalDate.of(2025, 12, 31),
                        LocalDate.of(2025, 1, 1),
                        0,
                        20));
    }

    @Test
    void endpointRetornaResultadoPaginadoEPermiteCorsDoFrontend() throws Exception {
        salvarDecisao(
                "decisao-api",
                "Dano moral por negativação indevida",
                "Consumidor inscrito indevidamente.",
                LocalDate.of(2025, 3, 15));

        mockMvc.perform(get("/api/jurisprudencias")
                        .param("termo", "negativação indevida")
                        .param("tribunal", "TJDFT")
                        .param("tamanho", "10")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:3000"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.itens[0].titulo").value("Dano moral por negativação indevida"))
                .andExpect(jsonPath("$.itens[0].decisao").value("Recurso parcialmente provido."))
                .andExpect(jsonPath("$.itens[0].tribunal").value("TJDFT"))
                .andExpect(jsonPath("$.itens[0].numeroProcesso")
                        .value("0702497-45.2026.8.07.0007"));

        mockMvc.perform(get("/api/documentos/decisoes")
                        .param("termo", "negativação indevida")
                        .param("tamanho", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].tipoDocumento").value("Acórdão"))
                .andExpect(jsonPath("$.itens[0].numeroProcessoOuTema")
                        .value("0702497-45.2026.8.07.0007"))
                .andExpect(jsonPath("$.itens[0].dataJulgamento").value("2025-03-15"))
                .andExpect(jsonPath("$.itens[0].decisao")
                        .value("Recurso parcialmente provido."));
    }

    private void salvarDecisao(
            String identificador,
            String titulo,
            String ementa,
            LocalDate dataJulgamento) {
        var decisao = NormalizedDocument.builder()
                .identificador(identificador)
                .titulo(titulo)
                .tipo("Acórdão")
                .resumo(ementa)
                .decisao("Recurso parcialmente provido.")
                .numeroProcesso("0702497-45.2026.8.07.0007")
                .relator("Desembargador Exemplo")
                .orgaoJulgador("2ª Turma Cível")
                .dataJulgamento(dataJulgamento)
                .dataPublicacao(dataJulgamento.plusDays(5))
                .url("https://example.test/decisoes/" + identificador)
                .metadados("{}")
                .build();
        processor.process(DocumentSource.TJDFT, fonte.getId(), registroBruto.getId(), decisao);
    }
}
