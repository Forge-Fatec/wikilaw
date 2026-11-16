package forge.wikilaw.backend.controller;

import forge.wikilaw.backend.entity.FonteDados;
import forge.wikilaw.backend.entity.RegistroBruto;
import forge.wikilaw.backend.entity.Tribunal;
import forge.wikilaw.backend.service.DocumentRecordProcessor;
import forge.wikilaw.backend.integration.documents.DocumentSource;
import forge.wikilaw.backend.integration.documents.NormalizedDocument;
import forge.wikilaw.backend.repository.FonteDadosRepository;
import forge.wikilaw.backend.repository.RegistroBrutoRepository;
import forge.wikilaw.backend.repository.TribunalRepository;
import forge.wikilaw.backend.repository.DecisaoJudicialRepository;
import static org.hamcrest.Matchers.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class BuscaDocumentalBDDIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired FonteDadosRepository fontes;
    @Autowired RegistroBrutoRepository registros;
    @Autowired TribunalRepository tribunais;
    @Autowired DocumentRecordProcessor processor;
    @Autowired DecisaoJudicialRepository decisoes;

    private Long importar(DocumentSource origem, String sigla, String identificador, NormalizedDocument documento) {
        var fonte = fontes.findAll().stream().filter(f -> sigla.equals(f.getSigla())).findFirst().orElseGet(() -> {
            var nova = new FonteDados();
            nova.setNome("Fonte " + sigla); nova.setSigla(sigla); nova.setTipoFonte("TESTE");
            return fontes.saveAndFlush(nova);
        });
        var bruto = new RegistroBruto();
        bruto.setIdentificadorExterno(identificador);
        bruto.setFonte(fonte); bruto.setFormatoPayload("JSON"); bruto.setPayloadTexto("{}");
        bruto.setDataColeta(OffsetDateTime.now());
        bruto.setHashConteudo(java.util.UUID.randomUUID().toString().replace("-", "") + "b".repeat(32));
        bruto = registros.saveAndFlush(bruto);
        return processor.process(origem, fonte.getId(), bruto.getId(), documento);
    }

    @BeforeEach
    void prepararDados() {
        if (tribunais.findBySigla("TJDFT").isEmpty()) {
            var tribunal = new Tribunal(); tribunal.setSigla("TJDFT"); tribunal.setNome("Tribunal de Justiça do DF");
            tribunais.saveAndFlush(tribunal);
        }
        if (tribunais.findBySigla("STJ").isEmpty()) {
            var tribunal = new Tribunal(); tribunal.setSigla("STJ"); tribunal.setNome("Superior Tribunal de Justiça");
            tribunais.saveAndFlush(tribunal);
        }
        importar(DocumentSource.TJDFT, "TJDFT", "bdd-decisao",
            NormalizedDocument.builder().identificador("bdd-decisao").titulo("Dano moral em relação de consumo")
                .resumo("Dano moral reconhecido em decisão judicial").numeroProcesso("0702497-45.2026.8.07.0007")
                .tribunal("TJDFT").orgaoJulgador("Primeira Turma").dataJulgamento(LocalDate.of(2026, 1, 9)).dataPublicacao(LocalDate.of(2026, 1, 10))
                .url("https://www.tjdft.jus.br/").build());
        importar(DocumentSource.STJ_PRECEDENTES, "STJ", "bdd-precedente",
            NormalizedDocument.builder().identificador("bdd-precedente").titulo("Tema sobre dano moral")
                .numeroTema("101").tese("Dano moral e responsabilidade civil").resumo("Questão sobre dano moral")
                .dataPublicacao(LocalDate.of(2025, 1, 10)).build());
        importar(DocumentSource.BDJUR, "BDJUR", "bdd-doutrina",
            NormalizedDocument.builder().identificador("bdd-doutrina").titulo("Estudo do dano moral")
                .resumo("Doutrina sobre dano moral").autores("Maria da Silva")
                .dataPublicacao(LocalDate.of(2024, 1, 10)).metadados("{}").build());
    }

    @Test @DisplayName("US01: termo encontra decisões, precedentes e doutrina")
    void deveEncontrarTermoNasTresCategorias() throws Exception {
        for (String categoria : new String[]{"decisoes", "precedentes", "doutrina"}) {
            mvc.perform(get("/api/documentos/{categoria}", categoria).param("termo", "dano"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.itens[0].categoria").value(categoria));
        }
    }

    @Test @DisplayName("US02: descrição é encaminhada à busca, incluindo várias palavras")
    void devePesquisarDescricaoComMultiplasPalavras() throws Exception {
        mvc.perform(get("/api/documentos/doutrina").param("termo", "dano moral"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test @DisplayName("US03: busca sem correspondências retorna lista vazia")
    void deveRetornarListaVaziaSemResultados() throws Exception {
        mvc.perform(get("/api/documentos/doutrina").param("termo", "termoimprovavelxyz123"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.itens.length()").value(0));
    }

    @Test @DisplayName("US04, US05: fonte e URL original são expostas no resultado")
    void deveExibirFonteEUrlOriginal() throws Exception {
        mvc.perform(get("/api/documentos/decisoes").param("termo", "relação de consumo").param("fonte", "TJDFT"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.itens[0].fonte").value("TJDFT"))
            .andExpect(jsonPath("$.itens[0].urlOriginal").value("https://www.tjdft.jus.br/"));
    }

    @Test @DisplayName("US07, US08, US09: processo, tribunal e ementa retornam na busca")
    void deveRetornarCamposJudiciais() throws Exception {
        mvc.perform(get("/api/documentos/decisoes").param("termo", "relação de consumo"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.itens[0].numeroProcessoOuTema").value("0702497-45.2026.8.07.0007"))
            .andExpect(jsonPath("$.itens[0].tribunal").value("TJDFT"))
            .andExpect(jsonPath("$.itens[0].resumoOuEmenta").value("Dano moral reconhecido em decisão judicial"));
    }

    @Test @DisplayName("US10: filtro por fonte seleciona somente documentos da fonte")
    void deveFiltrarPorFonte() throws Exception {
        mvc.perform(get("/api/documentos/doutrina").param("fonte", "BDJUR").param("termo", "dano moral"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.itens[0].fonte").value("BDJUR"));
    }

    @Test @DisplayName("US10: período sem correspondências não retorna documentos")
    void deveRespeitarPeriodo() throws Exception {
        mvc.perform(get("/api/documentos/decisoes").param("termo", "dano moral")
                .param("dataDe", "2035-01-01").param("dataAte", "2035-12-31"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test @DisplayName("Categoria inexistente retorna 404")
    void deveRejeitarCategoriaDesconhecida() throws Exception {
        mvc.perform(get("/api/documentos/categoria-inexistente")).andExpect(status().isNotFound());
    }

    // Cada cenário abaixo corresponde a um Given/When/Then da seção 4 da documentação.
    // Os testes usam requisições reais ao Controller e os repositories reais no H2.

    @Test @DisplayName("US02: termo válido retorna decisão compatível")
    void deveEncontrarDecisaoPorTermoValido() throws Exception {
        mvc.perform(get("/api/documentos/decisoes").param("termo", "consumo"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.itens[0].titulo", containsString("consumo")));
    }

    @Test @DisplayName("US02: busca com múltiplas palavras casa qualquer palavra relevante")
    void deveEncontrarPorUmaDasPalavrasRelevantes() throws Exception {
        mvc.perform(get("/api/documentos/decisoes").param("termo", "hipoteca consumo inexistente"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(1)));
    }

    @Test @DisplayName("US02: termo ausente retorna documentos ativos")
    void deveRetornarAtivosSemTermo() throws Exception {
        mvc.perform(get("/api/documentos/decisoes"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(1)));
    }

    @Test @DisplayName("US02: caracteres especiais de LIKE não alteram o resultado")
    void deveEscaparMetacaracteresDeLike() throws Exception {
        mvc.perform(get("/api/documentos/decisoes").param("termo", "%_\\"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test @DisplayName("US03: resultado informa campos obrigatórios e paginação")
    void deveRetornarTituloResumoTotalEPaginacao() throws Exception {
        mvc.perform(get("/api/documentos/decisoes").param("termo", "consumo"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.itens[0].titulo").isNotEmpty())
            .andExpect(jsonPath("$.itens[0].resumoOuEmenta").isNotEmpty())
            .andExpect(jsonPath("$.pagina").value(0)).andExpect(jsonPath("$.tamanho").value(20))
            .andExpect(jsonPath("$.total").isNumber()).andExpect(jsonPath("$.totalPaginas").isNumber());
    }

    @Test @DisplayName("US03: documento inativo não aparece na busca")
    void deveOcultarDocumentoInativo() throws Exception {
        var decisao = decisoes.findAll().stream().filter(d -> "bdd-decisao".equals(d.getIdentificadorExterno())).findFirst().orElseThrow();
        decisao.setAtivo(false);
        decisoes.saveAndFlush(decisao);
        mvc.perform(get("/api/documentos/decisoes").param("termo", "relação de consumo"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test @DisplayName("US04: resultado identifica a sigla da fonte")
    void deveIdentificarFonteDaDecisao() throws Exception {
        mvc.perform(get("/api/documentos/decisoes").param("termo", "consumo"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.itens[0].fonte").value("TJDFT"));
    }

    @Test @DisplayName("US05: URL original ausente retorna nulo")
    void deveAceitarUrlOriginalAusente() throws Exception {
        mvc.perform(get("/api/documentos/doutrina").param("termo", "Estudo do dano moral"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.itens[0].urlOriginal").value(nullValue()));
    }

    @Test @DisplayName("US06: decisões são ordenadas por data de julgamento decrescente")
    void deveOrdenarPorDataDeJulgamentoDecrescente() throws Exception {
        importar(DocumentSource.TJDFT, "TJDFT", "bdd-antiga",
            NormalizedDocument.builder().identificador("bdd-antiga").titulo("Dano moral antigo")
                .resumo("Dano moral anterior").dataJulgamento(LocalDate.of(2023, 1, 1)).metadados("{}").build());
        mvc.perform(get("/api/documentos/decisoes").param("termo", "dano moral"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.itens[0].identificadorExterno").value("bdd-decisao"))
            .andExpect(jsonPath("$.itens[1].identificadorExterno").value("bdd-antiga"));
    }

    @Test @DisplayName("US06: decisão sem data fica depois das decisões datadas")
    void devePosicionarSemDataAposDatadas() throws Exception {
        importar(DocumentSource.TJDFT, "TJDFT", "bdd-sem-data",
            NormalizedDocument.builder().identificador("bdd-sem-data").titulo("Dano moral sem data")
                .resumo("Dano moral sem data").metadados("{}").build());
        mvc.perform(get("/api/documentos/decisoes").param("termo", "dano moral"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.itens[0].identificadorExterno").value("bdd-decisao"))
            .andExpect(jsonPath("$.itens[1].identificadorExterno").value("bdd-sem-data"));
    }

    @Test @DisplayName("US07: decisão retorna número do processo")
    void deveExibirNumeroProcesso() throws Exception {
        mvc.perform(get("/api/documentos/decisoes").param("termo", "consumo"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.itens[0].numeroProcessoOuTema").value("0702497-45.2026.8.07.0007"));
    }

    @Test @DisplayName("US07: doutrina retorna número de processo nulo")
    void deveRetornarProcessoNuloNaDoutrina() throws Exception {
        mvc.perform(get("/api/documentos/doutrina").param("termo", "Estudo do dano moral"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.itens[0].numeroProcessoOuTema").value(nullValue()));
    }

    @Test @DisplayName("US08: decisão retorna tribunal e órgão julgador")
    void deveExibirTribunalEOrgao() throws Exception {
        mvc.perform(get("/api/documentos/decisoes").param("termo", "consumo"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.itens[0].tribunal").value("TJDFT"))
            .andExpect(jsonPath("$.itens[0].orgaoJulgador").value("Primeira Turma"));
    }

    @Test @DisplayName("US08: doutrina não possui tribunal nem órgão julgador")
    void deveRetornarTribunalEOrgaoNulosNaDoutrina() throws Exception {
        mvc.perform(get("/api/documentos/doutrina").param("termo", "Estudo do dano moral"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.itens[0].tribunal").value(nullValue()))
            .andExpect(jsonPath("$.itens[0].orgaoJulgador").value(nullValue()));
    }

    @Test @DisplayName("US09: retorna ementa, questão jurídica e resumo")
    void deveRetornarResumoDeCadaCategoria() throws Exception {
        for (String categoria : new String[]{"decisoes", "precedentes", "doutrina"}) {
            mvc.perform(get("/api/documentos/{categoria}", categoria).param("termo", "dano"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.itens[0].resumoOuEmenta").isNotEmpty());
        }
    }

    @Test @DisplayName("US09: texto de resumo ausente é retornado nulo")
    void deveRetornarResumoNuloQuandoAusente() throws Exception {
        importar(DocumentSource.BDJUR, "BDJUR", "bdd-sem-resumo",
            NormalizedDocument.builder().identificador("bdd-sem-resumo").titulo("Pesquisa sem resumo exclusivo")
                .metadados("{}").build());
        mvc.perform(get("/api/documentos/doutrina").param("termo", "exclusivo"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.itens[0].resumoOuEmenta").value(nullValue()));
    }

    @Test @DisplayName("US10: filtro por tribunal retorna somente decisões desse tribunal")
    void deveFiltrarPorTribunal() throws Exception {
        mvc.perform(get("/api/documentos/decisoes").param("termo", "consumo").param("tribunal", "TJDFT"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.itens[0].tribunal").value("TJDFT"));
    }

    @Test @DisplayName("US10: período seleciona somente decisões dentro do intervalo")
    void deveFiltrarDecisoesPorPeriodo() throws Exception {
        importar(DocumentSource.TJDFT, "TJDFT", "bdd-fora-periodo",
            NormalizedDocument.builder().identificador("bdd-fora-periodo").titulo("Dano moral antigo")
                .resumo("Dano moral anterior").dataJulgamento(LocalDate.of(2020, 1, 1)).metadados("{}").build());
        mvc.perform(get("/api/documentos/decisoes").param("termo", "dano moral")
                .param("dataDe", "2026-01-01").param("dataAte", "2026-12-31"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.itens[0].identificadorExterno").value("bdd-decisao"));
    }

    @Test @DisplayName("US10: fonte inexistente é rejeitada")
    void deveRejeitarFonteDesconhecida() throws Exception {
        mvc.perform(get("/api/documentos/decisoes").param("fonte", "FONTE_DESCONHECIDA"))
            .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("US10: tribunal inexistente é rejeitado")
    void deveRejeitarTribunalDesconhecido() throws Exception {
        mvc.perform(get("/api/documentos/decisoes").param("tribunal", "XYZ"))
            .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("US10: data inicial posterior à final é rejeitada")
    void deveRejeitarPeriodoInvertido() throws Exception {
        mvc.perform(get("/api/documentos/decisoes").param("dataDe", "2026-12-31").param("dataAte", "2026-01-01"))
            .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("US10: filtro válido sem correspondência retorna total zero")
    void deveRetornarListaVaziaParaFiltroValidoSemCorrespondencia() throws Exception {
        mvc.perform(get("/api/documentos/decisoes").param("tribunal", "TJDFT")
                .param("dataDe", "2035-01-01").param("dataAte", "2035-12-31"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.itens.length()").value(0));
    }

}
