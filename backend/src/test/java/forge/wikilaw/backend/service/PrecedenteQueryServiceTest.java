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
class PrecedenteQueryServiceTest {

    @Autowired
    private PrecedenteQueryService service;

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
        fonte.setNome("Precedentes Qualificados STJ");
        fonte.setSigla("STJ");
        fonte.setTipoFonte("PRECEDENTE");
        fonte = fontes.saveAndFlush(fonte);

        if (tribunais.findBySigla("STJ").isEmpty()) {
            Tribunal tribunal = new Tribunal();
            tribunal.setSigla("STJ");
            tribunal.setNome("Superior Tribunal de Justica");
            tribunais.saveAndFlush(tribunal);
        }

        registroBruto = new RegistroBruto();
        registroBruto.setFonte(fonte);
        registroBruto.setFormatoPayload("CSV");
        registroBruto.setDataColeta(OffsetDateTime.now());
        registroBruto.setHashConteudo("c".repeat(64));
        registroBruto.setPayloadTexto("{}");
        registroBruto = registrosBrutos.saveAndFlush(registroBruto);
    }

    @Test
    void buscaPorTermoTribunalEPeriodo() {
        salvarPrecedente(
                "tema-1234",
                "Repetitivo 1234",
                "Recurso Repetitivo",
                "1234",
                "Incidencia de prescricao intercorrente na execucao fiscal.",
                "A prescricao intercorrente corre automaticamente.",
                "Julgado",
                LocalDate.of(2025, 5, 10));
        salvarPrecedente(
                "tema-999",
                "Repercussao 999",
                "Repercussao Geral",
                "999",
                "Discussao sobre concurso publico.",
                "Nao ha direito subjetivo a nomeacao.",
                "Afetado",
                LocalDate.of(2023, 1, 20));

        var resultado = service.buscar(
                "PRESCRICAO INTERCORRENTE",
                "stj",
                "stj",
                null,
                null,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31),
                0,
                20);

        assertEquals(1, resultado.total());
        assertEquals("Repetitivo 1234", resultado.itens().getFirst().titulo());
        assertEquals("STJ", resultado.itens().getFirst().fonte());
        assertEquals("STJ", resultado.itens().getFirst().tribunal());
        assertEquals("1234", resultado.itens().getFirst().numeroTema());
        assertEquals(
                "A prescricao intercorrente corre automaticamente.",
                resultado.itens().getFirst().tese());
    }

    @Test
    void retornaResultadoQuandoQualquerPalavraChaveCombina() {
        salvarPrecedente(
                "tema-palavra-chave",
                "Repetitivo 4321",
                "Recurso Repetitivo",
                "4321",
                "Execucao fiscal e redirecionamento ao socio.",
                "Cabe redirecionamento quando ha dissolucao irregular.",
                "Julgado",
                LocalDate.of(2025, 6, 1));

        var resultado = service.buscar(
                "tributario inexistente consumidor dissolucao",
                null, null, null, null, null, null, 0, 20);

        assertEquals(1, resultado.total());
        assertEquals("Repetitivo 4321", resultado.itens().getFirst().titulo());
    }

    @Test
    void buscaPeloNumeroDoTema() {
        salvarPrecedente(
                "tema-1234",
                "Repetitivo 1234",
                "Recurso Repetitivo",
                "1234",
                "Questao submetida.",
                "Tese firmada.",
                "Julgado",
                LocalDate.of(2025, 5, 10));
        salvarPrecedente(
                "tema-999",
                "Repercussao 999",
                "Repercussao Geral",
                "999",
                "Outra questao.",
                "Outra tese.",
                "Afetado",
                LocalDate.of(2023, 1, 20));

        var resultado = service.buscar(
                "tema 1234", null, null, null, null, null, null, 0, 20);

        assertEquals(1, resultado.total());
        assertEquals("1234", resultado.itens().getFirst().numeroTema());
    }

    @Test
    void filtraPorSituacaoETipo() {
        salvarPrecedente(
                "tema-1234",
                "Repetitivo 1234",
                "Recurso Repetitivo",
                "1234",
                "Questao submetida.",
                "Tese firmada.",
                "Transito em julgado",
                LocalDate.of(2025, 5, 10));
        salvarPrecedente(
                "tema-999",
                "Repercussao 999",
                "Repercussao Geral",
                "999",
                "Outra questao.",
                "Outra tese.",
                "Afetado",
                LocalDate.of(2024, 1, 20));

        assertEquals(1, service.buscar(
                null, null, null, null, "julgado", null, null, 0, 20).total());
        assertEquals(1, service.buscar(
                null, null, null, "repetitivo", null, null, null, 0, 20).total());
        assertEquals(2, service.buscar(
                null, null, null, null, null, null, null, 0, 20).total());
    }

    @Test
    void ordenaDoJulgamentoMaisRecenteParaOMaisAntigo() {
        salvarPrecedente(
                "tema-antigo", "Repetitivo 100", "Recurso Repetitivo", "100",
                "Questao.", "Tese.", "Julgado", LocalDate.of(2020, 1, 1));
        salvarPrecedente(
                "tema-recente", "Repetitivo 200", "Recurso Repetitivo", "200",
                "Questao.", "Tese.", "Julgado", LocalDate.of(2026, 1, 1));

        var resultado = service.buscar(null, null, null, null, null, null, null, 0, 20);

        assertEquals("200", resultado.itens().getFirst().numeroTema());
    }

    @Test
    void ignoraPrecedenteInativo() {
        salvarPrecedente(
                "tema-inativo", "Repetitivo 555", "Recurso Repetitivo", "555",
                "Questao retirada.", "Tese.", "Julgado", LocalDate.of(2025, 2, 1));
        processor.indisponibilizar(DocumentSource.STJ_PRECEDENTES, fonte.getId(), "tema-inativo");

        var resultado = service.buscar(
                "questao retirada", null, null, null, null, null, null, 0, 20);

        assertEquals(0, resultado.total());
    }

    @Test
    void rejeitaPeriodoInvertido() {
        assertThrows(
                ResponseStatusException.class,
                () -> service.buscar(
                        null, null, null, null, null,
                        LocalDate.of(2025, 12, 31),
                        LocalDate.of(2025, 1, 1),
                        0,
                        20));
    }

    @Test
    void endpointDedicadoRetornaResultadoPaginadoEPermiteCorsDoFrontend() throws Exception {
        salvarPrecedente(
                "tema-1234",
                "Repetitivo 1234",
                "Recurso Repetitivo",
                "1234",
                "Incidencia de prescricao intercorrente na execucao fiscal.",
                "A prescricao intercorrente corre automaticamente.",
                "Transito em julgado",
                LocalDate.of(2025, 5, 10));

        mockMvc.perform(get("/api/precedentes")
                        .param("termo", "prescricao intercorrente")
                        .param("tribunal", "STJ")
                        .param("tamanho", "10")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:3000"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.itens[0].numeroTema").value("1234"))
                .andExpect(jsonPath("$.itens[0].tipoPrecedente").value("Recurso Repetitivo"))
                .andExpect(jsonPath("$.itens[0].situacao").value("Transito em julgado"))
                .andExpect(jsonPath("$.itens[0].tese")
                        .value("A prescricao intercorrente corre automaticamente."))
                .andExpect(jsonPath("$.itens[0].tribunal").value("STJ"));
    }

    @Test
    void endpointGenericoAplicaFiltroDeTribunalEPeriodo() throws Exception {
        salvarPrecedente(
                "tema-1234", "Repetitivo 1234", "Recurso Repetitivo", "1234",
                "Questao recente.", "Tese recente.", "Julgado", LocalDate.of(2025, 5, 10));
        salvarPrecedente(
                "tema-999", "Repercussao 999", "Repercussao Geral", "999",
                "Questao antiga.", "Tese antiga.", "Afetado", LocalDate.of(2023, 1, 20));

        mockMvc.perform(get("/api/documentos/precedentes")
                        .param("tribunal", "stj")
                        .param("dataDe", "2025-01-01")
                        .param("dataAte", "2025-12-31")
                        .param("tamanho", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.itens[0].numeroProcessoOuTema").value("1234"))
                .andExpect(jsonPath("$.itens[0].tribunal").value("STJ"))
                .andExpect(jsonPath("$.itens[0].dataJulgamento").value("2025-05-10"))
                // A tese firmada ocupa o campo "decisao" do resumo generico.
                .andExpect(jsonPath("$.itens[0].decisao").value("Tese recente."));
    }

    @Test
    void endpointGenericoNaoDevolveTeseVaziaComoTextoEmBranco() throws Exception {
        // Controversia do CSV do STJ chega com teseFirmada vazia.
        salvarPrecedente(
                "controversia-sem-tese", "Controversia 7", "Controversia", "7",
                "Questao ainda submetida.", "", "Vinculada a Tema", LocalDate.of(2025, 4, 1));

        mockMvc.perform(get("/api/documentos/precedentes").param("tamanho", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].decisao").doesNotExist());
    }

    @Test
    void endpointGenericoRejeitaPeriodoInvertido() throws Exception {
        mockMvc.perform(get("/api/documentos/precedentes")
                        .param("dataDe", "2025-12-31")
                        .param("dataAte", "2025-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void endpointRejeitaTribunalDesconhecido() throws Exception {
        mockMvc.perform(get("/api/precedentes").param("tribunal", "XXX"))
                .andExpect(status().isBadRequest());
    }

    private void salvarPrecedente(
            String identificador,
            String titulo,
            String tipo,
            String numeroTema,
            String questaoJuridica,
            String tese,
            String situacao,
            LocalDate dataJulgamento) {
        var precedente = NormalizedDocument.builder()
                .identificador(identificador)
                .titulo(titulo)
                .tipo(tipo)
                .numeroTema(numeroTema)
                .resumo(questaoJuridica)
                .tese(tese)
                .situacao(situacao)
                .dataJulgamento(dataJulgamento)
                .dataPublicacao(dataJulgamento.plusDays(5))
                .url("https://example.test/precedentes/" + identificador)
                .metadados("{}")
                .build();
        processor.process(
                DocumentSource.STJ_PRECEDENTES, fonte.getId(), registroBruto.getId(), precedente);
    }
}
