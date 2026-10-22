package forge.wikilaw.backend.service;

import forge.wikilaw.backend.entity.*;
import forge.wikilaw.backend.integration.documents.DocumentSource;
import forge.wikilaw.backend.integration.documents.NormalizedDocument;
import forge.wikilaw.backend.repository.*;
import forge.wikilaw.backend.service.semantic.SemanticContentBackfillService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class SemanticContentPreparationTest {
    @Autowired DocumentRecordProcessor processor;
    @Autowired FonteDadosRepository sources;
    @Autowired RegistroBrutoRepository rawRecords;
    @Autowired TribunalRepository tribunals;
    @Autowired DocumentoSemanticoRepository semanticDocuments;
    @Autowired TrechoSemanticoRepository chunks;
    @Autowired DocumentoDoutrinarioRepository doctrine;
    @Autowired SemanticContentBackfillService backfill;

    @Test
    void preparesDoctrineIdempotentlyAndRebuildsItWhenContentChanges() {
        RegistroBruto raw = raw("BDJUR");
        var document = NormalizedDocument.builder()
                .identificador("doutrina-1")
                .titulo("Responsabilidade civil do fornecedor")
                .tipo("ARTIGO")
                .resumo("<p>A responsabilidade independe de culpa.</p>")
                .autores("Maria Silva")
                .palavrasChave("consumidor; dano moral")
                .metadados("{}")
                .build();

        Long id = processor.process(DocumentSource.BDJUR, raw.getFonte().getId(), raw.getId(), document);
        DocumentoSemantico semantic = semanticDocuments.findByIdDocumentoDoutrinario(id).orElseThrow();
        var originalChunks = chunks.findByIdDocumentoSemanticoOrderByOrdem(semantic.getId());
        String originalHash = semantic.getHashConteudo();

        assertEquals(DocumentoSemantico.Categoria.DOUTRINA, semantic.getCategoria());
        assertEquals(DocumentoSemantico.Status.PRONTO, semantic.getStatus());
        assertEquals(2, originalChunks.size());
        assertEquals("RESUMO", originalChunks.getFirst().getSecao());
        assertFalse(originalChunks.getFirst().getTexto().contains("<p>"));
        assertTrue(originalChunks.getFirst().getTextoParaEmbedding().contains("Categoria: DOUTRINA"));
        assertTrue(originalChunks.getFirst().getTextoParaEmbedding().contains("Titulo: Responsabilidade civil"));

        processor.process(DocumentSource.BDJUR, raw.getFonte().getId(), raw.getId(), document);
        assertEquals(originalHash, semanticDocuments.findByIdDocumentoDoutrinario(id).orElseThrow().getHashConteudo());
        assertEquals(2, chunks.countByIdDocumentoSemantico(semantic.getId()));

        var updated = NormalizedDocument.builder()
                .identificador("doutrina-1")
                .titulo("Responsabilidade civil do fornecedor")
                .tipo("ARTIGO")
                .resumo("A responsabilidade objetiva protege o consumidor vulneravel.")
                .autores("Maria Silva")
                .palavrasChave("consumidor; dano moral")
                .metadados("{}")
                .build();
        processor.process(DocumentSource.BDJUR, raw.getFonte().getId(), raw.getId(), updated);

        DocumentoSemantico changed = semanticDocuments.findByIdDocumentoDoutrinario(id).orElseThrow();
        assertNotEquals(originalHash, changed.getHashConteudo());
        assertTrue(chunks.findByIdDocumentoSemanticoOrderByOrdem(changed.getId()).getFirst()
                .getTexto().contains("responsabilidade objetiva"));

        processor.indisponibilizar(DocumentSource.BDJUR, raw.getFonte().getId(), "doutrina-1");
        assertEquals(DocumentoSemantico.Status.INATIVO,
                semanticDocuments.findByIdDocumentoDoutrinario(id).orElseThrow().getStatus());
    }

    @Test
    void preservesSpecificSectionsForDecisionsAndPrecedents() {
        tribunal("TJDFT");
        RegistroBruto decisionRaw = raw("TJDFT");
        var decision = NormalizedDocument.builder()
                .identificador("acordao-1").titulo("Acórdão consumidor")
                .tipo("ACORDAO").resumo("Negativação indevida.")
                .decisao("Pedido julgado procedente.")
                .inteiroTeor("Fundamentos sobre o CDC e reparação integral.")
                .build();
        Long decisionId = processor.process(DocumentSource.TJDFT,
                decisionRaw.getFonte().getId(), decisionRaw.getId(), decision);
        var decisionSemantic = semanticDocuments.findByIdDecisao(decisionId).orElseThrow();
        assertEquals(java.util.List.of("EMENTA", "DECISAO", "INTEIRO_TEOR"),
                chunks.findByIdDocumentoSemanticoOrderByOrdem(decisionSemantic.getId()).stream()
                        .map(TrechoSemantico::getSecao).toList());

        tribunal("STJ");
        RegistroBruto precedentRaw = raw("STJPRECEDENTES");
        var precedent = NormalizedDocument.builder()
                .identificador("tema-1").titulo("Tema repetitivo 1")
                .tipo("REPETITIVO").numeroTema("1")
                .resumo("Definir se há responsabilidade objetiva.")
                .tese("O fornecedor responde independentemente de culpa.")
                .build();
        Long precedentId = processor.process(DocumentSource.STJ_PRECEDENTES,
                precedentRaw.getFonte().getId(), precedentRaw.getId(), precedent);
        var precedentSemantic = semanticDocuments.findByIdPrecedente(precedentId).orElseThrow();
        assertEquals(java.util.List.of("QUESTAO_JURIDICA", "TESE"),
                chunks.findByIdDocumentoSemanticoOrderByOrdem(precedentSemantic.getId()).stream()
                        .map(TrechoSemantico::getSecao).toList());
    }

    @Test
    void backfillPreparesDocumentsThatExistedBeforeTheSemanticTables() {
        RegistroBruto raw = raw("SCIELO");
        var oldDocument = new DocumentoDoutrinario();
        oldDocument.setIdFonte(raw.getFonte().getId());
        oldDocument.setIdRegistroBruto(raw.getId());
        oldDocument.setIdentificadorExterno("artigo-antigo");
        oldDocument.setTitulo("Artigo já armazenado");
        oldDocument.setResumo("Conteúdo anterior à implantação da busca semântica.");
        oldDocument.setAtualizadoEm(OffsetDateTime.now());
        oldDocument = doctrine.saveAndFlush(oldDocument);

        assertTrue(semanticDocuments.findByIdDocumentoDoutrinario(oldDocument.getId()).isEmpty());
        assertTrue(backfill.processBatch(10) >= 1);
        assertEquals(DocumentoSemantico.Status.PRONTO,
                semanticDocuments.findByIdDocumentoDoutrinario(oldDocument.getId()).orElseThrow().getStatus());
    }

    private RegistroBruto raw(String source) {
        var existing = sources.findBySigla(source);
        FonteDados dataSource = existing.orElseGet(() -> {
            var value = new FonteDados();
            value.setNome(source);
            value.setSigla(source);
            value.setTipoFonte("TESTE");
            return sources.saveAndFlush(value);
        });
        var raw = new RegistroBruto();
        raw.setFonte(dataSource);
        raw.setFormatoPayload("JSON");
        raw.setDataColeta(OffsetDateTime.now());
        raw.setHashConteudo("b".repeat(64));
        raw.setPayloadTexto("{}");
        return rawRecords.saveAndFlush(raw);
    }

    private void tribunal(String acronym) {
        if (tribunals.findBySigla(acronym).isEmpty()) {
            var tribunal = new Tribunal();
            tribunal.setSigla(acronym);
            tribunal.setNome(acronym);
            tribunals.saveAndFlush(tribunal);
        }
    }
}

