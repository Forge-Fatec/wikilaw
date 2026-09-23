package forge.wikilaw.backend.service;

import forge.wikilaw.backend.dto.DocumentoDetalheResponse;
import forge.wikilaw.backend.entity.*;
import forge.wikilaw.backend.repository.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly=true)
public class DocumentQueryService {
    private final DecisaoJudicialRepository decisions;
    private final PrecedenteRepository precedents;
    private final DocumentoDoutrinarioRepository doctrine;
    private final FonteDadosRepository sources;
    private final TribunalRepository tribunals;
    private final PrecedenteProcessoRepository links;
    private final JurisprudenciaQueryService jurisprudence;
    private final PrecedenteQueryService precedentSearch;
    public DocumentQueryService(DecisaoJudicialRepository decisions,PrecedenteRepository precedents,
            DocumentoDoutrinarioRepository doctrine,FonteDadosRepository sources,TribunalRepository tribunals,
            PrecedenteProcessoRepository links,JurisprudenciaQueryService jurisprudence,
            PrecedenteQueryService precedentSearch) {
        this.decisions=decisions;this.precedents=precedents;this.doctrine=doctrine;this.sources=sources;
        this.tribunals=tribunals;this.links=links;this.jurisprudence=jurisprudence;
        this.precedentSearch=precedentSearch;
    }
    public Result list(String category,String source,String term,int page,int size) {
        return list(category,source,term,null,null,null,page,size);
    }
    public Result list(String category,String source,String term,String tribunal,
            LocalDate dateFrom,LocalDate dateTo,int page,int size) {
        Map<Long,String> sourceNames=new HashMap<>();
        sources.findAll().forEach(f -> sourceNames.put(f.getId(),f.getSigla()));
        Map<Long,String> tribunalNames=new HashMap<>();
        tribunals.findAll().forEach(t -> tribunalNames.put(t.getId(),t.getSigla()));
        Pageable paging=PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id"));
        Page<? extends DocumentoBase> result=switch(category) {
            case "decisoes" -> jurisprudence.buscarPagina(
                    term,source,tribunal,dateFrom,dateTo,page,size);
            case "precedentes" -> precedentSearch.buscarPagina(
                    term,source,tribunal,null,null,dateFrom,dateTo,page,size);
            case "doutrina" -> doctrine.findAll(filter(sourceId(source),term,"resumo","autores","palavrasChave"),paging);
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        };
        var rows=result.getContent().stream()
            .map(d -> toSummary(d,category,sourceNames,tribunalNames)).toList();
        return new Result(rows,page,size,result.getTotalElements(),result.getTotalPages());
    }
    /**
     * Monta o resumo de exibição. Os campos "ou" (numeroProcessoOuTema,
     * autoresOuRelator, resumoOuEmenta) existem porque a tela usa um card único
     * para as três categorias; os demais só vêm preenchidos na categoria a que
     * pertencem e chegam nulos nas outras.
     */
    private Summary toSummary(DocumentoBase d,String category,
            Map<Long,String> sourceNames,Map<Long,String> tribunalNames) {
        DecisaoJudicial decisao=d instanceof DecisaoJudicial j?j:null;
        Precedente precedente=d instanceof Precedente p?p:null;
        DocumentoDoutrinario doutrina=d instanceof DocumentoDoutrinario a?a:null;
        return new Summary(
            d.getId(),
            sourceNames.get(d.getIdFonte()),
            category,
            d.getIdentificadorExterno(),
            d.getTitulo(),
            decisao!=null?decisao.getTipoDecisao()
                :precedente!=null?precedente.getTipoPrecedente():doutrina.getTipoDocumento(),
            decisao!=null?decisao.getNumeroProcesso():precedente!=null?precedente.getNumeroTema():null,
            decisao!=null?decisao.getRelator():doutrina!=null?doutrina.getAutores():null,
            decisao!=null?decisao.getEmenta()
                :doutrina!=null?doutrina.getResumo():precedente.getQuestaoJuridica(),
            // Para o precedente, o equivalente ao dispositivo da decisão é a tese firmada.
            decisao!=null?decisao.getDecisao():precedente!=null?textoOuNulo(precedente.getTese()):null,
            tribunalNames.get(tribunalIdOf(d)),
            orgaoJulgadorOf(d),
            precedente!=null?textoOuNulo(precedente.getSituacao()):null,
            decisao!=null?decisao.isPossuiInteiroTeor():null,
            doutrina!=null?textoOuNulo(doutrina.getPeriodico()):null,
            doutrina!=null?textoOuNulo(doutrina.getDoi()):null,
            doutrina!=null?textoOuNulo(doutrina.getIssn()):null,
            doutrina!=null?textoOuNulo(doutrina.getIdioma()):null,
            doutrina!=null?textoOuNulo(doutrina.getPalavrasChave()):null,
            decisao!=null?decisao.getDataJulgamento():precedente!=null?precedente.getDataJulgamento():null,
            d.getDataPublicacao(),d.getDataOriginal(),d.getUrlOriginal(),d.getIdRegistroBruto());
    }
    /**
     * Tema sem tese firmada chega como string vazia no CSV do STJ. O frontend usa
     * {@code ??} para cair no texto padrão, que só cobre null — então normaliza aqui.
     */
    private String textoOuNulo(String valor) {
        return valor==null || valor.isBlank()?null:valor;
    }
    private Long sourceId(String source) {
        if (source==null) return null;
        return sources.findBySigla(source.toUpperCase(Locale.ROOT))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,"Fonte desconhecida")).getId();
    }
    public DocumentoDetalheResponse detail(String category,Long id) {
        var d=buscarAtivo(category,id);
        DecisaoJudicial decisao=d instanceof DecisaoJudicial j?j:null;
        Precedente precedente=d instanceof Precedente p?p:null;
        DocumentoDoutrinario doutrina=d instanceof DocumentoDoutrinario a?a:null;
        return new DocumentoDetalheResponse(
            d.getId(),
            category,
            sources.findById(d.getIdFonte()).map(FonteDados::getSigla).orElse(null),
            d.getIdentificadorExterno(),
            d.getTitulo(),
            decisao!=null?decisao.getTipoDecisao()
                :precedente!=null?precedente.getTipoPrecedente():doutrina.getTipoDocumento(),
            tribunalSigla(tribunalIdOf(d)),
            orgaoJulgadorOf(d),
            decisao!=null?decisao.getNumeroProcesso():null,
            precedente!=null?precedente.getNumeroTema():null,
            decisao!=null?decisao.getRelator():null,
            doutrina!=null?doutrina.getAutores():null,
            decisao!=null?decisao.getEmenta():null,
            precedente!=null?precedente.getQuestaoJuridica():null,
            doutrina!=null?doutrina.getResumo():null,
            decisao!=null?decisao.getDecisao():null,
            precedente!=null?textoOuNulo(precedente.getTese()):null,
            precedente!=null?textoOuNulo(precedente.getSituacao()):null,
            decisao!=null?decisao.getInteiroTeor():null,
            decisao!=null?decisao.isPossuiInteiroTeor():null,
            doutrina!=null?textoOuNulo(doutrina.getPeriodico()):null,
            doutrina!=null?textoOuNulo(doutrina.getDoi()):null,
            doutrina!=null?textoOuNulo(doutrina.getIssn()):null,
            doutrina!=null?textoOuNulo(doutrina.getIdioma()):null,
            doutrina!=null?textoOuNulo(doutrina.getPalavrasChave()):null,
            decisao!=null?decisao.getDataJulgamento():precedente!=null?precedente.getDataJulgamento():null,
            d.getDataPublicacao(),d.getDataOriginal(),d.getUrlOriginal());
    }
    /** Documento retirado pela fonte (ativo = false) não é exibível. */
    DocumentoBase buscarAtivo(String category,Long id) {
        Optional<? extends DocumentoBase> d=switch(category) {
            case "decisoes" -> decisions.findById(id);
            case "precedentes" -> precedents.findById(id);
            case "doutrina" -> doctrine.findById(id);
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        };
        return d.filter(DocumentoBase::isAtivo).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Documento não encontrado"));
    }
    private String tribunalSigla(Long idTribunal) {
        if (idTribunal==null) return null;
        return tribunals.findById(idTribunal).map(Tribunal::getSigla).orElse(null);
    }
    public List<PrecedenteProcesso> related(Long id) {
        buscarAtivo("precedentes",id);
        return links.findByIdPrecedenteOrderByNumeroRegistro(id);
    }
    /**
     * Doutrina não tem tribunal (não é vinculada a um órgão julgador).
     * Decisão e precedente têm id_tribunal, mas cada entidade guarda o campo
     * separadamente (não existe no DocumentoBase), então é preciso checar o tipo.
     */
    private Long tribunalIdOf(DocumentoBase d) {
        if (d instanceof DecisaoJudicial j) return j.getIdTribunal();
        if (d instanceof Precedente p) return p.getIdTribunal();
        return null;
    }
    /**
     * Só decisão judicial guarda o nome do órgão julgador (texto livre, vindo
     * da fonte original). Precedente e doutrina não têm esse campo.
     */
    private String orgaoJulgadorOf(DocumentoBase d) {
        if (d instanceof DecisaoJudicial j) return j.getOrgaoJulgador();
        return null;
    }
    private <T extends DocumentoBase> Specification<T> filter(Long source,String term,String... fields) {
        return (root,query,cb) -> {
            var restrictions=new ArrayList<jakarta.persistence.criteria.Predicate>();
            restrictions.add(cb.isTrue(root.get("ativo")));
            if (source!=null) restrictions.add(cb.equal(root.get("idFonte"),source));
            if (term!=null && !term.isBlank()) {
                String pattern="%"+term.toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
                var alternatives=new ArrayList<jakarta.persistence.criteria.Predicate>();
                alternatives.add(cb.like(cb.lower(root.get("titulo")),pattern,'\\'));
                for(String field:fields) alternatives.add(cb.like(cb.lower(root.get(field)),pattern,'\\'));
                restrictions.add(cb.or(alternatives.toArray(jakarta.persistence.criteria.Predicate[]::new)));
            }
            return cb.and(restrictions.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
    /**
     * Campos de exibição da listagem. Nenhum campo anterior foi removido ou
     * renomeado: o frontend atual continua lendo o mesmo contrato.
     */
    public record Summary(Long id,String fonte,String categoria,String identificadorExterno,
        String titulo,String tipoDocumento,String numeroProcessoOuTema,
        String autoresOuRelator,String resumoOuEmenta,String decisao,
        String tribunal,String orgaoJulgador,
        String situacao,Boolean possuiInteiroTeor,
        String periodico,String doi,String issn,String idioma,String palavrasChave,
        java.time.LocalDate dataJulgamento,java.time.LocalDate dataPublicacao,String dataOriginal,String urlOriginal,Long idRegistroBruto) {}
    public record Result(List<Summary> itens,int pagina,int tamanho,long total,int totalPaginas) {}
}
