package forge.wikilaw.backend.service.semantic;

import forge.wikilaw.backend.dto.SimilaridadeRequest;
import forge.wikilaw.backend.service.DocumentQueryService;
import forge.wikilaw.backend.service.SearchContextService;
import forge.wikilaw.backend.service.search.SearchTermProcessor;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VectorSearchService {
    private final boolean enabled;
    private final VectorStore store;
    private final EmbeddingClient embeddings;
    private final LegalTextChunker chunker;
    private final DocumentQueryService documents;
    private final SearchContextService context;
    private final SearchTermProcessor terms;

    public VectorSearchService(@Value("${wikilaw.vector.enabled:false}") boolean enabled, VectorStore store,
            EmbeddingClient embeddings, LegalTextChunker chunker, DocumentQueryService documents,
            SearchContextService context, SearchTermProcessor terms) {
        this.enabled=enabled; this.store=store; this.embeddings=embeddings; this.chunker=chunker;
        this.documents=documents; this.context=context; this.terms=terms;
    }

    public Status status() {
        if (!enabled) return new Status(false,0,0);
        var counts=store.counts();
        return new Status(true,counts.indexados(),counts.pendentes());
    }

    public Results search(SimilaridadeRequest request) {
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"A busca por similaridade não está habilitada neste servidor. Escolha a busca textual.");
        var counts=store.counts();
        if (counts.indexados()==0) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "O acervo ainda não possui documentos indexados para similaridade. Aguarde a preparação ou escolha a busca textual.");
        int limit=request.limite()==null?100:request.limite();
        var texts=new ArrayList<String>();
        if (request.termo()!=null && !request.termo().isBlank()) texts.add(request.termo().trim());
        if (request.texto()!=null && !request.texto().isBlank()) texts.addAll(chunker.split(request.texto()));
        // Every input section participates; no first-page or first-300-character truncation.
        var scores=new HashMap<String,Double>();
        var best=new HashMap<String,VectorStore.Hit>();
        int queryIndex=0;
        boolean hasDescription=request.termo()!=null && !request.termo().isBlank();
        int contextCount=texts.size()-(hasDescription?1:0);
        for (float[] vector : embeddings.embed(texts,"query")) {
            double weight=hasDescription && queryIndex++==0 ? 1.0 : 1.0/Math.max(1,contextCount);
            var hits=unique(store.nearest(vector,Math.min(limit*3,600)));
            int rank=0;
            for (var hit : hits) {
                scores.merge(hit.key(),weight/(60 + ++rank),Double::sum);
                best.merge(hit.key(),hit,(a,b)->a.similaridade()>=b.similaridade()?a:b);
            }
        }
        String lexical=request.termo();
        if (request.texto()!=null && !request.texto().isBlank()) {
            try { lexical=context.prepare(request.termo(),request.texto()).consulta(); }
            catch (forge.wikilaw.backend.exception.DocumentUploadException exception) {
                // A numeric identifier can be embedded even if there are no lexical context words.
                lexical=request.termo();
            }
        }
        String fullText=String.join(" OR ", terms.tokenize(lexical));
        int rank=0;
        for (var hit : unique(store.lexical(fullText,Math.min(limit*3,600)))) {
            // Text matches refine candidates that also have a vector match.
            if (best.containsKey(hit.key())) scores.merge(hit.key(),1.0/(60 + ++rank),Double::sum);
        }
        var ordered=best.values().stream().sorted(Comparator
                .comparingDouble((VectorStore.Hit hit)->scores.get(hit.key())).reversed()
                .thenComparing(VectorStore.Hit::key)).limit(limit).toList();
        var summaries=new HashMap<String,DocumentQueryService.Summary>();
        for (String category:List.of("decisoes","precedentes","doutrina")) {
            var ids=ordered.stream().filter(hit->category.equals(hit.apiCategory())).map(VectorStore.Hit::id).toList();
            if (!ids.isEmpty()) for (var summary:documents.summariesByIds(category,ids)) summaries.put(category+":"+summary.id(),summary);
        }
        var items=ordered.stream().filter(hit->summaries.containsKey(hit.apiCategory()+":"+hit.id()))
                .map(hit->new Item(summaries.get(hit.apiCategory()+":"+hit.id()),hit.similaridade(),hit.trecho())).toList();
        return new Results(items,counts.indexados(),counts.pendentes(),limit);
    }

    private List<VectorStore.Hit> unique(List<VectorStore.Hit> hits) {
        var map=new LinkedHashMap<String,VectorStore.Hit>();
        hits.forEach(hit->map.putIfAbsent(hit.key(),hit));
        return List.copyOf(map.values());
    }
    public record Status(boolean habilitada,long indexados,long pendentes) {}
    public record Item(DocumentQueryService.Summary documento,double similaridade,String trecho) {}
    public record Results(List<Item> itens,long indexados,long pendentes,int limite) {}
}
