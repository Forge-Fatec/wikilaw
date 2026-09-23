package forge.wikilaw.backend.service;

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
    public DocumentQueryService(DecisaoJudicialRepository decisions,PrecedenteRepository precedents,
            DocumentoDoutrinarioRepository doctrine,FonteDadosRepository sources,TribunalRepository tribunals,
            PrecedenteProcessoRepository links,JurisprudenciaQueryService jurisprudence) {
        this.decisions=decisions;this.precedents=precedents;this.doctrine=doctrine;this.sources=sources;
        this.tribunals=tribunals;this.links=links;this.jurisprudence=jurisprudence;
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
            case "precedentes" -> precedents.findAll(filter(sourceId(source),term,"questaoJuridica","tese","situacao"),paging);
            case "doutrina" -> doctrine.findAll(filter(sourceId(source),term,"resumo","autores","palavrasChave"),paging);
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        };
        var rows=result.getContent().stream().map(d -> new Summary(d.getId(),sourceNames.get(d.getIdFonte()),category,
            d.getTitulo(),
            d instanceof DecisaoJudicial j?j.getTipoDecisao():d instanceof Precedente p?p.getTipoPrecedente():((DocumentoDoutrinario)d).getTipoDocumento(),
            d instanceof DecisaoJudicial j?j.getNumeroProcesso():d instanceof Precedente p?p.getNumeroTema():null,
            d instanceof DecisaoJudicial j?j.getRelator():d instanceof DocumentoDoutrinario a?a.getAutores():null,
            d instanceof DecisaoJudicial j?j.getEmenta():d instanceof DocumentoDoutrinario a?a.getResumo():((Precedente)d).getQuestaoJuridica(),
            d instanceof DecisaoJudicial j?j.getDecisao():null,
            tribunalNames.get(tribunalIdOf(d)),
            orgaoJulgadorOf(d),
            d instanceof DecisaoJudicial j?j.getDataJulgamento():d instanceof Precedente p?p.getDataJulgamento():null,
            d.getDataPublicacao(),d.getDataOriginal(),d.getUrlOriginal(),d.getIdRegistroBruto())).toList();
        return new Result(rows,page,size,result.getTotalElements(),result.getTotalPages());
    }
    private Long sourceId(String source) {
        if (source==null) return null;
        return sources.findBySigla(source.toUpperCase(Locale.ROOT))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,"Fonte desconhecida")).getId();
    }
    public DocumentoBase detail(String category,Long id) {
        Optional<? extends DocumentoBase> d=switch(category) {
            case "decisoes" -> decisions.findById(id);
            case "precedentes" -> precedents.findById(id);
            case "doutrina" -> doctrine.findById(id);
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        };
        return d.filter(DocumentoBase::isAtivo).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Documento não encontrado"));
    }
    public List<PrecedenteProcesso> related(Long id) {
        detail("precedentes",id);
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
    public record Summary(Long id,String fonte,String categoria,String titulo,String tipoDocumento,String numeroProcessoOuTema,
        String autoresOuRelator,String resumoOuEmenta,String decisao,
        String tribunal,String orgaoJulgador,
        java.time.LocalDate dataJulgamento,java.time.LocalDate dataPublicacao,String dataOriginal,String urlOriginal,Long idRegistroBruto) {}
    public record Result(List<Summary> itens,int pagina,int tamanho,long total,int totalPaginas) {}
}
