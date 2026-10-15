package forge.wikilaw.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import forge.wikilaw.backend.dto.DashboardFiltro;
import forge.wikilaw.backend.dto.DashboardResponse;
import forge.wikilaw.backend.dto.DashboardResponse.DistribuicaoItem;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class DashboardServiceTest {

    @Autowired
    private DashboardService service;

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
        registroBruto.setHashConteudo("d".repeat(64));
        registroBruto.setPayloadTexto("{}");
        registroBruto = registrosBrutos.saveAndFlush(registroBruto);
    }

    @Test
    void resumeIndicadoresEDistribuicoesDoRecorte() {
        salvarDecisao("d1", "Dano moral", "Recurso provido.", "1ª Turma Cível", LocalDate.of(2025, 1, 10));
        salvarDecisao("d2", "Dano moral", "Recurso parcialmente provido.", "1ª Turma Cível", LocalDate.of(2025, 2, 10));
        salvarDecisao("d3", "Dano moral", "Recurso desprovido.", "2ª Turma Cível", LocalDate.of(2025, 3, 10));
        salvarDecisao("d4", "Dano moral", "Negaram provimento ao recurso.", null, LocalDate.of(2025, 4, 10));
        salvarDecisao("d5", "Dano moral", "Homologado o acordo.", "  2ª   Turma Cível ", LocalDate.of(2025, 5, 10));

        var resumo = service.resumir(DashboardFiltro.semFiltros());

        assertEquals(new DashboardResponse.Indicadores(5, 4, 2, 50), resumo.indicadores());
        assertEquals(List.of(
                new DistribuicaoItem("Provido", 1),
                new DistribuicaoItem("Parcialmente provido", 1),
                new DistribuicaoItem("Desprovido", 2),
                new DistribuicaoItem("Não identificado", 1)),
                resumo.distribuicaoMerito());
        assertEquals(List.of(
                new DistribuicaoItem("1ª Turma Cível", 2),
                new DistribuicaoItem("2ª Turma Cível", 2),
                new DistribuicaoItem("Não informado", 1)),
                resumo.distribuicaoCamaraTurma());
    }

    @Test
    void aplicaTermoEPeriodoDaPesquisaAtiva() {
        salvarDecisao("d1", "Dano moral por negativação", "Recurso provido.", "1ª Turma Cível", LocalDate.of(2025, 1, 10));
        salvarDecisao("d2", "Dano moral por negativação", "Recurso desprovido.", "2ª Turma Cível", LocalDate.of(2023, 1, 10));
        salvarDecisao("d3", "Concurso público", "Recurso desprovido.", "3ª Turma Cível", LocalDate.of(2025, 1, 10));

        var resumo = service.resumir(new DashboardFiltro(
                "negativação", "tjdft", "tjdft", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)));

        assertEquals(new DashboardResponse.Indicadores(1, 1, 1, 100), resumo.indicadores());
        assertEquals(List.of(new DistribuicaoItem("1ª Turma Cível", 1)), resumo.distribuicaoCamaraTurma());
    }

    @Test
    void agrupaOrgaosExcedentesEmOutros() {
        for (int i = 1; i <= DashboardService.LIMITE_CATEGORIAS + 2; i++) {
            salvarDecisao("d" + i, "Dano moral", "Recurso provido.", i + "ª Turma Cível", LocalDate.of(2025, 1, i));
        }

        var distribuicao = service.resumir(DashboardFiltro.semFiltros()).distribuicaoCamaraTurma();

        assertEquals(DashboardService.LIMITE_CATEGORIAS, distribuicao.size());
        assertEquals(new DistribuicaoItem(DashboardService.OUTROS, 3), distribuicao.getLast());
    }

    @Test
    void fonteOuTribunalSemCadastroResultaEmRecorteVazio() {
        salvarDecisao("d1", "Dano moral", "Recurso provido.", "1ª Turma Cível", LocalDate.of(2025, 1, 10));

        var resumo = service.resumir(new DashboardFiltro(null, null, "STF", null, null));

        assertEquals(new DashboardResponse.Indicadores(0, 0, 0, null), resumo.indicadores());
        assertNull(resumo.indicadores().aderencia());
        assertEquals(List.of(), resumo.distribuicaoCamaraTurma());
        assertEquals(0, resumo.distribuicaoMerito().stream().mapToLong(DistribuicaoItem::quantidade).sum());
    }

    @Test
    void endpointRespondeComCorsDoFrontend() throws Exception {
        salvarDecisao("d1", "Dano moral", "Recurso provido.", "1ª Turma Cível", LocalDate.of(2025, 1, 10));

        mockMvc.perform(get("/api/dashboard")
                        .param("termo", "dano moral")
                        .param("fonte", "Fonte desconhecida")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(jsonPath("$.indicadores.totalDecisoes").value(0));

        mockMvc.perform(get("/api/dashboard").param("termo", "dano moral"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicadores.totalDecisoes").value(1))
                .andExpect(jsonPath("$.indicadores.aderencia").value(100))
                .andExpect(jsonPath("$.distribuicaoMerito[0].categoria").value("Provido"))
                .andExpect(jsonPath("$.distribuicaoMerito[0].quantidade").value(1))
                .andExpect(jsonPath("$.distribuicaoCamaraTurma[0].categoria").value("1ª Turma Cível"));
    }

    @Test
    void distribuiPorTribunal() {
        var fonteStj = new FonteDados();
        fonteStj.setNome("Jurisprudência STJ");
        fonteStj.setSigla("STJ");
        fonteStj.setTipoFonte("JURISPRUDENCIA");
        fonteStj = fontes.saveAndFlush(fonteStj);

        var stj = new Tribunal();
        stj.setSigla("STJ");
        stj.setNome("Superior Tribunal de Justiça");
        stj.setUf("DF");
        tribunais.saveAndFlush(stj);

        salvarDecisao("d1", "Dano moral", "Recurso provido.", "1ª Turma Cível", LocalDate.of(2025, 1, 10));
        salvarDecisao("d2", "Dano moral", "Recurso provido.", "1ª Turma Cível", LocalDate.of(2025, 1, 11));
        var decisaoStj = documento("d3", "Dano moral", "Recurso desprovido.", "Terceira Turma", LocalDate.of(2025, 1, 12));
        processor.process(DocumentSource.STJ, fonteStj.getId(), registroBruto.getId(), decisaoStj);

        assertEquals(List.of(
                new DistribuicaoItem("TJDFT", 2),
                new DistribuicaoItem("STJ", 1)),
                service.distribuicaoTribunal(DashboardFiltro.semFiltros()));
        assertEquals(List.of(new DistribuicaoItem("STJ", 1)),
                service.distribuicaoTribunal(new DashboardFiltro(null, "stj", null, null, null)));
    }

    @Test
    void evolucaoAnualEmOrdemCronologicaComDecisoesSemDataNoFim() {
        salvarDecisao("d1", "Dano moral", "Recurso provido.", "1ª Turma Cível", LocalDate.of(2025, 3, 10));
        salvarDecisao("d2", "Dano moral", "Recurso provido.", "1ª Turma Cível", LocalDate.of(2023, 1, 10));
        salvarDecisao("d3", "Dano moral", "Recurso provido.", "1ª Turma Cível", LocalDate.of(2025, 8, 10));
        salvarDecisao("d4", "Dano moral", "Recurso provido.", "1ª Turma Cível", null);

        assertEquals(List.of(
                new DistribuicaoItem("2023", 1),
                new DistribuicaoItem("2025", 2),
                new DistribuicaoItem(DashboardService.SEM_DATA, 1)),
                service.evolucaoAnual(DashboardFiltro.semFiltros()));
    }

    @Test
    void endpointsPorAgregacaoRespondemComOMesmoRecorteDoResumo() throws Exception {
        salvarDecisao("d1", "Dano moral", "Recurso provido.", "1ª Turma Cível", LocalDate.of(2024, 1, 10));
        salvarDecisao("d2", "Dano moral", "Recurso desprovido.", "2ª Turma Cível", LocalDate.of(2025, 1, 10));
        salvarDecisao("d3", "Concurso público", "Recurso desprovido.", "2ª Turma Cível", LocalDate.of(2025, 1, 10));

        mockMvc.perform(get("/api/dashboard").param("termo", "dano moral"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicadores.totalDecisoes").value(2))
                .andExpect(jsonPath("$.distribuicaoTribunal[0].categoria").value("TJDFT"))
                .andExpect(jsonPath("$.evolucaoAnual.length()").value(2));

        mockMvc.perform(get("/api/dashboard/indicadores").param("termo", "dano moral"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDecisoes").value(2))
                .andExpect(jsonPath("$.aderencia").value(50));
        mockMvc.perform(get("/api/dashboard/distribuicao-merito").param("termo", "dano moral"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[2].categoria").value("Desprovido"))
                .andExpect(jsonPath("$[2].quantidade").value(1));
        mockMvc.perform(get("/api/dashboard/distribuicao-camara-turma").param("termo", "dano moral"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/dashboard/distribuicao-tribunal").param("termo", "dano moral"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoria").value("TJDFT"))
                .andExpect(jsonPath("$[0].quantidade").value(2));
        mockMvc.perform(get("/api/dashboard/evolucao-anual")
                        .param("termo", "dano moral")
                        .param("dataDe", "2025-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].categoria").value("2025"));
    }

    @Test
    void endpointRejeitaParametroInvalido() throws Exception {
        mockMvc.perform(get("/api/dashboard/indicadores").param("dataDe", "31/12/2025"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/dashboard").param("tribunal", "T".repeat(21)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.startsWith("tribunal")));
    }

    @Test
    void endpointRejeitaPeriodoInvertido() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                        .param("dataDe", "2025-12-31")
                        .param("dataAte", "2025-01-01"))
                .andExpect(status().isBadRequest());
    }

    private void salvarDecisao(
            String identificador,
            String titulo,
            String decisao,
            String orgaoJulgador,
            LocalDate dataJulgamento) {
        var documento = documento(identificador, titulo, decisao, orgaoJulgador, dataJulgamento);
        processor.process(DocumentSource.TJDFT, fonte.getId(), registroBruto.getId(), documento);
    }

    private NormalizedDocument documento(
            String identificador,
            String titulo,
            String decisao,
            String orgaoJulgador,
            LocalDate dataJulgamento) {
        return NormalizedDocument.builder()
                .identificador(identificador)
                .titulo(titulo)
                .tipo("Acórdão")
                .resumo("Ementa de teste.")
                .decisao(decisao)
                .orgaoJulgador(orgaoJulgador)
                .dataJulgamento(dataJulgamento)
                .dataPublicacao(dataJulgamento == null ? null : dataJulgamento.plusDays(5))
                .metadados("{}")
                .build();
    }
}
