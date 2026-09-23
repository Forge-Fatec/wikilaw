package forge.wikilaw.backend.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contrato de exibição da API de documentos (SCRUM-157).
 *
 * <p>Cobre o que a listagem precisa entregar por categoria e garante que o
 * detalhe deixou de serializar a entidade JPA com campos internos.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class DocumentQueryServiceTest {

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

    @Test
    void listagemDeDoutrinaTrazDadosBibliograficos() throws Exception {
        var registro = registro("BDJUR");
        processor.process(DocumentSource.BDJUR, registro.getFonte().getId(), registro.getId(),
                NormalizedDocument.builder()
                        .identificador("doc-1")
                        .titulo("A prescricao no direito tributario")
                        .tipo("Artigo")
                        .resumo("Estudo sobre prazos prescricionais.")
                        .autores("Maria Silva; Joao Souza")
                        .periodico("Revista de Direito Tributario")
                        .doi("10.1234/rdt.2025.42")
                        .issn("1808-2432")
                        .idioma("pt")
                        .palavrasChave("prescricao; tributario; decadencia")
                        .dataPublicacao(LocalDate.of(2025, 3, 1))
                        .metadados("{}")
                        .build());

        mockMvc.perform(get("/api/documentos/doutrina").param("tamanho", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].identificadorExterno").value("doc-1"))
                .andExpect(jsonPath("$.itens[0].periodico").value("Revista de Direito Tributario"))
                .andExpect(jsonPath("$.itens[0].doi").value("10.1234/rdt.2025.42"))
                .andExpect(jsonPath("$.itens[0].issn").value("1808-2432"))
                .andExpect(jsonPath("$.itens[0].idioma").value("pt"))
                .andExpect(jsonPath("$.itens[0].palavrasChave")
                        .value("prescricao; tributario; decadencia"))
                // Campos de outras categorias chegam nulos.
                .andExpect(jsonPath("$.itens[0].situacao").doesNotExist())
                .andExpect(jsonPath("$.itens[0].possuiInteiroTeor").doesNotExist());
    }

    @Test
    void listagemDePrecedenteTrazSituacao() throws Exception {
        tribunal("STJ");
        var registro = registro("STJ");
        processor.process(DocumentSource.STJ_PRECEDENTES, registro.getFonte().getId(), registro.getId(),
                NormalizedDocument.builder()
                        .identificador("tema-1234")
                        .titulo("Repetitivo 1234")
                        .tipo("Recurso Repetitivo")
                        .numeroTema("1234")
                        .resumo("Questao submetida.")
                        .tese("Tese firmada.")
                        .situacao("Transito em julgado")
                        .metadados("{}")
                        .build());

        mockMvc.perform(get("/api/documentos/precedentes").param("tamanho", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].situacao").value("Transito em julgado"))
                .andExpect(jsonPath("$.itens[0].numeroProcessoOuTema").value("1234"))
                .andExpect(jsonPath("$.itens[0].periodico").doesNotExist());
    }

    @Test
    void listagemDeDecisaoInformaSeTemInteiroTeor() throws Exception {
        tribunal("TJDFT");
        var registro = registro("TJDFT");
        processor.process(DocumentSource.TJDFT, registro.getFonte().getId(), registro.getId(),
                NormalizedDocument.builder()
                        .identificador("decisao-1")
                        .titulo("Acordao de exemplo")
                        .tipo("Acordao")
                        .resumo("Ementa do acordao.")
                        .decisao("Recurso provido.")
                        .inteiroTeor("Texto completo do acordao.")
                        .numeroProcesso("0702497-45.2026.8.07.0007")
                        .relator("Desembargador Exemplo")
                        .orgaoJulgador("2a Turma Civel")
                        .dataJulgamento(LocalDate.of(2025, 5, 10))
                        .metadados("{}")
                        .build());

        mockMvc.perform(get("/api/documentos/decisoes").param("tamanho", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].possuiInteiroTeor").value(true))
                // O inteiro teor em si não vai na listagem, só no detalhe.
                .andExpect(jsonPath("$.itens[0].inteiroTeor").doesNotExist());
    }

    @Test
    void listagemMantemOsCamposQueOFrontendJaConsome() throws Exception {
        tribunal("TJDFT");
        var registro = registro("TJDFT");
        processor.process(DocumentSource.TJDFT, registro.getFonte().getId(), registro.getId(),
                NormalizedDocument.builder()
                        .identificador("decisao-contrato")
                        .titulo("Acordao de exemplo")
                        .tipo("Acordao")
                        .resumo("Ementa do acordao.")
                        .decisao("Recurso provido.")
                        .numeroProcesso("0702497-45.2026.8.07.0007")
                        .relator("Desembargador Exemplo")
                        .orgaoJulgador("2a Turma Civel")
                        .dataJulgamento(LocalDate.of(2025, 5, 10))
                        .dataPublicacao(LocalDate.of(2025, 5, 15))
                        .url("https://example.test/decisoes/1")
                        .metadados("{}")
                        .build());

        mockMvc.perform(get("/api/documentos/decisoes").param("tamanho", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].id").exists())
                .andExpect(jsonPath("$.itens[0].fonte").value("TJDFT"))
                .andExpect(jsonPath("$.itens[0].categoria").value("decisoes"))
                .andExpect(jsonPath("$.itens[0].titulo").value("Acordao de exemplo"))
                .andExpect(jsonPath("$.itens[0].tipoDocumento").value("Acordao"))
                .andExpect(jsonPath("$.itens[0].numeroProcessoOuTema")
                        .value("0702497-45.2026.8.07.0007"))
                .andExpect(jsonPath("$.itens[0].autoresOuRelator").value("Desembargador Exemplo"))
                .andExpect(jsonPath("$.itens[0].resumoOuEmenta").value("Ementa do acordao."))
                .andExpect(jsonPath("$.itens[0].decisao").value("Recurso provido."))
                .andExpect(jsonPath("$.itens[0].tribunal").value("TJDFT"))
                .andExpect(jsonPath("$.itens[0].orgaoJulgador").value("2a Turma Civel"))
                .andExpect(jsonPath("$.itens[0].dataJulgamento").value("2025-05-10"))
                .andExpect(jsonPath("$.itens[0].dataPublicacao").value("2025-05-15"))
                .andExpect(jsonPath("$.itens[0].urlOriginal").value("https://example.test/decisoes/1"));
    }

    @Test
    void detalheNaoExpoeCamposInternos() throws Exception {
        tribunal("STJ");
        var registro = registro("STJ");
        var id = processor.process(DocumentSource.STJ_PRECEDENTES,
                registro.getFonte().getId(), registro.getId(),
                NormalizedDocument.builder()
                        .identificador("tema-7")
                        .titulo("Repetitivo 7")
                        .tipo("Recurso Repetitivo")
                        .numeroTema("7")
                        .resumo("Questao submetida.")
                        .tese("Tese firmada.")
                        .situacao("Julgado")
                        .metadados("{\"payload\":\"bruto da fonte\"}")
                        .build());

        mockMvc.perform(get("/api/documentos/precedentes/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadados").doesNotExist())
                .andExpect(jsonPath("$.ativo").doesNotExist())
                .andExpect(jsonPath("$.idFonte").doesNotExist())
                .andExpect(jsonPath("$.idTribunal").doesNotExist())
                .andExpect(jsonPath("$.idRegistroBruto").doesNotExist())
                .andExpect(jsonPath("$.atualizadoEm").doesNotExist());
    }

    @Test
    void detalheTrazSiglaDeFonteETribunalEmVezDeId() throws Exception {
        tribunal("STJ");
        var registro = registro("STJ");
        var id = processor.process(DocumentSource.STJ_PRECEDENTES,
                registro.getFonte().getId(), registro.getId(),
                NormalizedDocument.builder()
                        .identificador("tema-8")
                        .titulo("Repetitivo 8")
                        .tipo("Recurso Repetitivo")
                        .numeroTema("8")
                        .resumo("Questao submetida.")
                        .tese("Tese firmada.")
                        .situacao("Julgado")
                        .metadados("{}")
                        .build());

        mockMvc.perform(get("/api/documentos/precedentes/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria").value("precedentes"))
                .andExpect(jsonPath("$.fonte").value("STJ"))
                .andExpect(jsonPath("$.tribunal").value("STJ"))
                .andExpect(jsonPath("$.identificadorExterno").value("tema-8"))
                .andExpect(jsonPath("$.numeroTema").value("8"))
                .andExpect(jsonPath("$.questaoJuridica").value("Questao submetida."))
                .andExpect(jsonPath("$.tese").value("Tese firmada."))
                .andExpect(jsonPath("$.situacao").value("Julgado"))
                // Campos de decisão e doutrina não se aplicam a precedente.
                .andExpect(jsonPath("$.numeroProcesso").doesNotExist())
                .andExpect(jsonPath("$.periodico").doesNotExist());
    }

    @Test
    void detalheDeDecisaoTrazInteiroTeor() throws Exception {
        tribunal("TJDFT");
        var registro = registro("TJDFT");
        var id = processor.process(DocumentSource.TJDFT,
                registro.getFonte().getId(), registro.getId(),
                NormalizedDocument.builder()
                        .identificador("decisao-2")
                        .titulo("Acordao de exemplo")
                        .tipo("Acordao")
                        .resumo("Ementa do acordao.")
                        .decisao("Recurso provido.")
                        .inteiroTeor("Texto completo do acordao.")
                        .relator("Desembargador Exemplo")
                        .metadados("{}")
                        .build());

        mockMvc.perform(get("/api/documentos/decisoes/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ementa").value("Ementa do acordao."))
                .andExpect(jsonPath("$.decisao").value("Recurso provido."))
                .andExpect(jsonPath("$.inteiroTeor").value("Texto completo do acordao."))
                .andExpect(jsonPath("$.possuiInteiroTeor").value(true))
                .andExpect(jsonPath("$.relator").value("Desembargador Exemplo"));
    }

    @Test
    void detalheDeDoutrinaTrazDadosBibliograficos() throws Exception {
        var registro = registro("BDJUR");
        var id = processor.process(DocumentSource.BDJUR,
                registro.getFonte().getId(), registro.getId(),
                NormalizedDocument.builder()
                        .identificador("doc-2")
                        .titulo("Artigo de exemplo")
                        .tipo("Artigo")
                        .resumo("Resumo do artigo.")
                        .autores("Maria Silva")
                        .periodico("Revista de Direito Tributario")
                        .doi("10.1234/rdt.2025.42")
                        .issn("1808-2432")
                        .idioma("pt")
                        .palavrasChave("prescricao")
                        .metadados("{}")
                        .build());

        mockMvc.perform(get("/api/documentos/doutrina/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fonte").value("BDJUR"))
                .andExpect(jsonPath("$.autores").value("Maria Silva"))
                .andExpect(jsonPath("$.resumo").value("Resumo do artigo."))
                .andExpect(jsonPath("$.periodico").value("Revista de Direito Tributario"))
                .andExpect(jsonPath("$.doi").value("10.1234/rdt.2025.42"))
                // Doutrina não tem tribunal.
                .andExpect(jsonPath("$.tribunal").doesNotExist());
    }

    @Test
    void detalheDeDocumentoRetiradoPelaFonteRetorna404() throws Exception {
        var registro = registro("BDJUR");
        var id = processor.process(DocumentSource.BDJUR,
                registro.getFonte().getId(), registro.getId(),
                NormalizedDocument.builder()
                        .identificador("doc-3")
                        .titulo("Artigo retirado")
                        .resumo("Resumo.")
                        .metadados("{}")
                        .build());
        processor.indisponibilizar(DocumentSource.BDJUR, registro.getFonte().getId(), "doc-3");

        mockMvc.perform(get("/api/documentos/doutrina/" + id))
                .andExpect(status().isNotFound());
    }

    private RegistroBruto registro(String sigla) {
        var fonte = new FonteDados();
        fonte.setNome(sigla);
        fonte.setSigla(sigla);
        fonte.setTipoFonte("TESTE");
        fonte = fontes.saveAndFlush(fonte);

        var registro = new RegistroBruto();
        registro.setFonte(fonte);
        registro.setFormatoPayload("JSON");
        registro.setDataColeta(OffsetDateTime.now());
        registro.setHashConteudo("d".repeat(64));
        registro.setPayloadTexto("{}");
        return registrosBrutos.saveAndFlush(registro);
    }

    private void tribunal(String sigla) {
        if (tribunais.findBySigla(sigla).isEmpty()) {
            var tribunal = new Tribunal();
            tribunal.setSigla(sigla);
            tribunal.setNome(sigla);
            tribunais.saveAndFlush(tribunal);
        }
    }
}
