package forge.wikilaw.backend.service;

import forge.wikilaw.backend.entity.*;
import forge.wikilaw.backend.repository.*;
import forge.wikilaw.backend.integration.documents.*;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class DocumentPersistenceTest {
    @Autowired DocumentRecordProcessor processor;
    @Autowired FonteDadosRepository sources;
    @Autowired RegistroBrutoRepository raws;
    @Autowired TribunalRepository tribunals;
    @Autowired DecisaoJudicialRepository decisions;
    @Autowired DocumentoDoutrinarioRepository doctrine;
    @Autowired PrecedenteRepository precedents;
    @Autowired PrecedenteProcessoRepository links;
    @Autowired ProcessoRepository processes;
    @Autowired DocumentQueryService queries;

    private RegistroBruto raw(String source) {
        var f=new FonteDados(); f.setNome(source); f.setSigla(source); f.setTipoFonte("TESTE");
        f=sources.saveAndFlush(f);
        var r=new RegistroBruto(); r.setFonte(f); r.setFormatoPayload("JSON");
        r.setDataColeta(OffsetDateTime.now());r.setHashConteudo("a".repeat(64));r.setPayloadTexto("{}");
        return raws.saveAndFlush(r);
    }
    private void tribunal(String name) {
        if (tribunals.findBySigla(name).isEmpty()) {
            var t=new Tribunal();t.setSigla(name);t.setNome(name);tribunals.saveAndFlush(t);
        }
    }
    @Test void reimportUpdatesDoctrineAndReadableQueryFindsAuthor() {
        var r=raw("BDJUR");
        var n=NormalizedDocument.builder().identificador("doc1").titulo("Título").autores("Maria")
            .resumo("Resumo").metadados("{}").build();
        long before=doctrine.count();
        var id=processor.process(DocumentSource.BDJUR,r.getFonte().getId(),r.getId(),n);
        var updated=NormalizedDocument.builder().identificador("doc1").titulo("Título atualizado").autores("Maria")
            .resumo("Resumo novo").metadados("{}").build();
        assertEquals(id,processor.process(DocumentSource.BDJUR,r.getFonte().getId(),r.getId(),updated));
        assertEquals(before+1,doctrine.count());
        assertEquals("Resumo novo",doctrine.findById(id).orElseThrow().getResumo());
        assertEquals(1,queries.list("doutrina","BDJUR","Maria",0,10).total());
        processor.indisponibilizar(DocumentSource.BDJUR,r.getFonte().getId(),"doc1");
        assertEquals(0,queries.list("doutrina","BDJUR","Maria",0,10).total());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->queries.detail("doutrina",id));
        processor.process(DocumentSource.BDJUR,r.getFonte().getId(),r.getId(),updated);
        assertEquals(1,queries.list("doutrina","BDJUR","Maria",0,10).total());
    }
    @Test void decisionsReuseCnjButNeverTurnStjShortNumbersIntoCnj() {
        tribunal("TJDFT");
        var r=raw("TJDFT");
        var n=NormalizedDocument.builder().identificador("decisao1").titulo("Acórdão")
            .numeroProcesso("0702497-45.2026.8.07.0007").resumo("Ementa").build();
        long before=processes.count();
        var id=processor.process(DocumentSource.TJDFT,r.getFonte().getId(),r.getId(),n);
        assertEquals(id,processor.process(DocumentSource.TJDFT,r.getFonte().getId(),r.getId(),n));
        assertEquals(before+1,processes.count());
        assertNotNull(decisions.findById(id).orElseThrow().getIdProcesso());
        tribunal("STJ");
        var stj=raw("STJ");
        var shortNumber=NormalizedDocument.builder().identificador("stj1").titulo("STJ").numeroProcesso("123").build();
        var stjId=processor.process(DocumentSource.STJ,stj.getFonte().getId(),stj.getId(),shortNumber);
        assertNull(decisions.findById(stjId).orElseThrow().getIdProcesso());
        assertEquals(before+1,processes.count());
    }
    @Test void precedentLinksAreReplacedWithoutDuplicates() {
        tribunal("STJ");
        var r=raw("STJ");
        var related=new NormalizedDocument.RelatedProcess("2026000001","REsp 1","Relator","S",r.getId());
        var n=NormalizedDocument.builder().identificador("tema1").titulo("Tema 1")
            .tipo("Tema").numeroTema("1").tese("Tese").processosRelacionados(List.of(related,related)).build();
        var id=processor.process(DocumentSource.STJ_PRECEDENTES,r.getFonte().getId(),r.getId(),n);
        assertEquals(id,processor.process(DocumentSource.STJ_PRECEDENTES,r.getFonte().getId(),r.getId(),n));
        assertEquals(1,links.findByIdPrecedenteOrderByNumeroRegistro(id).size());
        assertEquals("Tese",precedents.findById(id).orElseThrow().getTese());
    }

    @Test void pangeaUpsertsPrecedentsWithoutAssumingEveryOriginIsStj() {
        var r=raw("PANGEA");
        var n=NormalizedDocument.builder().identificador("tjba-nt-1").titulo("TJBA — NT nº 1")
            .tribunal("TJBA").tipo("NT").numeroTema("1").tese("Texto").build();
        var id=processor.process(DocumentSource.PANGEA,r.getFonte().getId(),r.getId(),n);
        assertEquals(id,processor.process(DocumentSource.PANGEA,r.getFonte().getId(),r.getId(),n));
        var p=precedents.findById(id).orElseThrow();
        assertEquals("TJBA",p.getTribunalOrigem());
        assertNull(p.getIdTribunal());
        assertEquals(1,queries.list("precedentes","PANGEA",null,0,10).total());
        assertEquals(0,queries.list("doutrina","PANGEA",null,0,10).total());
        processor.indisponibilizar(DocumentSource.PANGEA,r.getFonte().getId(),n.identificador());
        assertEquals(0,queries.list("precedentes","PANGEA",null,0,10).total());
    }
}
