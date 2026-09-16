package forge.wikilaw.backend.service;

import forge.wikilaw.backend.entity.*;
import forge.wikilaw.backend.repository.*;
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
    private final PrecedenteProcessoRepository links;
    public DocumentQueryService(DecisaoJudicialRepository decisions,PrecedenteRepository precedents,
            DocumentoDoutrinarioRepository doctrine,FonteDadosRepository sources,PrecedenteProcessoRepository links) {
        this.decisions=decisions;this.precedents=precedents;this.doctrine=doctrine;this.sources=sources;this.links=links;
    }
    public Result list(String category,String source,String term,int page,int size) {
        Map<Long,String> names=new HashMap<>();
        sources.findAll().forEach(f -> names.put(f.getId(),f.getSigla()));
        Long sourceId=source==null?null:sources.findBySigla(source.toUpperCase(Locale.ROOT))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,"Fonte desconhecida")).getId();
        Pageable paging=PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id"));
        Page<? extends DocumentoBase> result=switch(category) {
            case "decisoes" -> decisions.findAll(filter(sourceId,term,"ementa","relator","numeroProcesso"),paging);
            case "precedentes" -> precedents.findAll(filter(sourceId,term,"questaoJuridica","tese","situacao"),paging);
            case "doutrina" -> doctrine.findAll(filter(sourceId,term,"resumo","autores","palavrasChave"),paging);
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        };
        var rows=result.getContent().stream().map(d -> new Summary(d.getId(),names.get(d.getIdFonte()),category,
            d.getTitulo(),d instanceof DecisaoJudicial j?j.getRelator():d instanceof DocumentoDoutrinario a?a.getAutores():null,
            d instanceof DecisaoJudicial j?j.getEmenta():d instanceof DocumentoDoutrinario a?a.getResumo():((Precedente)d).getQuestaoJuridica(),
            d.getDataPublicacao(),d.getDataOriginal(),d.getUrlOriginal(),d.getIdRegistroBruto())).toList();
        return new Result(rows,page,size,result.getTotalElements(),result.getTotalPages());
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
    public record Summary(Long id,String fonte,String categoria,String titulo,String autoresOuRelator,String resumoOuEmenta,
        java.time.LocalDate dataPublicacao,String dataOriginal,String urlOriginal,Long idRegistroBruto) {}
    public record Result(List<Summary> itens,int pagina,int tamanho,long total,int totalPaginas) {}
}
