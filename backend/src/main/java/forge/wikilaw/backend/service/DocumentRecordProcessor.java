package forge.wikilaw.backend.service;

import forge.wikilaw.backend.entity.*;
import forge.wikilaw.backend.repository.*;
import forge.wikilaw.backend.integration.documents.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentRecordProcessor {
    private final DecisaoJudicialRepository decisions;
    private final PrecedenteRepository precedents;
    private final DocumentoDoutrinarioRepository doctrine;
    private final TribunalRepository tribunals;
    private final ProcessoRepository processes;
    private final PrecedenteProcessoRepository links;

    public DocumentRecordProcessor(DecisaoJudicialRepository decisions,PrecedenteRepository precedents,
            DocumentoDoutrinarioRepository doctrine,TribunalRepository tribunals,ProcessoRepository processes,
            PrecedenteProcessoRepository links) {
        this.decisions=decisions; this.precedents=precedents; this.doctrine=doctrine;
        this.tribunals=tribunals; this.processes=processes; this.links=links;
    }
    @Transactional
    public Long process(DocumentSource source,Long sourceId,Long rawId,NormalizedDocument n) {
        if (n.identificador()==null || n.identificador().isBlank() || n.identificador().length()>300 ||
                n.titulo()==null || n.titulo().isBlank()) throw new IllegalArgumentException("Identificador/título inválido");
        if (source==DocumentSource.STJ || source==DocumentSource.TJDFT) {
            var d=decisions.findByIdFonteAndIdentificadorExterno(sourceId,n.identificador()).orElseGet(DecisaoJudicial::new);
            common(d,sourceId,rawId,n);
            d.setNumeroProcesso(n.numeroProcesso()); d.setTipoDecisao(n.tipo()); d.setEmenta(n.resumo());
            d.setRelator(n.relator()); d.setOrgaoJulgador(n.orgaoJulgador()); d.setDataJulgamento(n.dataJulgamento());
            d.setDecisao(n.decisao()); d.setInteiroTeor(n.inteiroTeor());
            d.setPossuiInteiroTeor(n.inteiroTeor()!=null && !n.inteiroTeor().isBlank());
            d.setIdTribunal(tribunals.findBySigla(source.sigla()).orElseThrow().getId());
            // Identificadores de registro/classe STJ não são números CNJ.
            String cnj=n.numeroProcesso()==null?"":n.numeroProcesso().replaceAll("[.\\-/\\s]","");
            if (cnj.matches("[0-9]{20}")) {
                var p=processes.findByNumeroCnj(cnj).orElseGet(() -> {
                    var created=new Processo(); created.setNumeroCnj(cnj); return processes.save(created);
                });
                d.setIdProcesso(p.getId());
            } else d.setIdProcesso(null);
            return decisions.saveAndFlush(d).getId();
        }
        if (source==DocumentSource.STJ_PRECEDENTES) {
            var p=precedents.findByIdFonteAndIdentificadorExterno(sourceId,n.identificador()).orElseGet(Precedente::new);
            common(p,sourceId,rawId,n);
            p.setNumeroTema(n.numeroTema()); p.setTipoPrecedente(n.tipo()); p.setQuestaoJuridica(n.resumo());
            p.setTese(n.tese()); p.setSituacao(n.situacao()); p.setDataJulgamento(n.dataJulgamento());
            p.setIdTribunal(tribunals.findBySigla("STJ").orElseThrow().getId());
            p=precedents.saveAndFlush(p);
            links.deleteByIdPrecedente(p.getId());
            links.flush();
            if (n.processosRelacionados()!=null) {
                var seen=new java.util.HashSet<String>();
                for (var related:n.processosRelacionados()) {
                    if (!seen.add(related.numeroRegistro())) continue;
                    var link=new PrecedenteProcesso();
                    link.setIdPrecedente(p.getId()); link.setIdRegistroBruto(related.registroBrutoId());
                    link.setNumeroRegistro(related.numeroRegistro()); link.setDescricao(related.descricao());
                    link.setRelator(related.relator()); link.setLeadingCase(related.leadingCase());
                    links.save(link);
                }
            }
            links.flush();
            return p.getId();
        }
        var d=doctrine.findByIdFonteAndIdentificadorExterno(sourceId,n.identificador()).orElseGet(DocumentoDoutrinario::new);
        common(d,sourceId,rawId,n);
        d.setTipoDocumento(n.tipo()); d.setResumo(n.resumo()); d.setAutores(n.autores()); d.setDoi(n.doi());
        d.setIdioma(n.idioma()); d.setPeriodico(n.periodico()); d.setIssn(n.issn()); d.setPalavrasChave(n.palavrasChave());
        return doctrine.saveAndFlush(d).getId();
    }
    private void common(DocumentoBase d,Long fonte,Long bruto,NormalizedDocument n) {
        d.setAtivo(true);
        d.setIdFonte(fonte); d.setIdRegistroBruto(bruto); d.setIdentificadorExterno(n.identificador());
        d.setTitulo(n.titulo()); d.setUrlOriginal(n.url()); d.setDataPublicacao(n.dataPublicacao());
        d.setDataOriginal(n.dataOriginal()); d.setMetadados(n.metadados());
        d.setAtualizadoEm(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public void indisponibilizar(DocumentSource source,Long fonte,String identificador) {
        DocumentoBase d;
        if (source==DocumentSource.STJ || source==DocumentSource.TJDFT)
            d=decisions.findByIdFonteAndIdentificadorExterno(fonte,identificador).orElse(null);
        else if (source==DocumentSource.STJ_PRECEDENTES)
            d=precedents.findByIdFonteAndIdentificadorExterno(fonte,identificador).orElse(null);
        else d=doctrine.findByIdFonteAndIdentificadorExterno(fonte,identificador).orElse(null);
        if (d!=null) {
            d.setAtivo(false);
            d.setAtualizadoEm(OffsetDateTime.now(ZoneOffset.UTC));
        }
    }
}
