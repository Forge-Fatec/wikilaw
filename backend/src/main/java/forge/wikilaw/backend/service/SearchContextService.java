package forge.wikilaw.backend.service;

import forge.wikilaw.backend.exception.DocumentUploadException;
import forge.wikilaw.backend.service.search.SearchTermProcessor;
import forge.wikilaw.backend.service.semantic.LegalTextNormalizer;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Bridges temporary document text into the existing lexical search; it does not generate embeddings. */
@Service
public class SearchContextService {
    private static final Set<String> COMMON = Set.of("seu", "sua", "seus", "suas", "este", "esta", "esse", "essa", "deste", "desta", "dessa", "desse", "mais", "como", "também", "ser", "foi", "pelo", "pela", "pelos", "pelas", "aos", "sobre", "neste", "nesta", "entre", "pode", "não", "sim", "são", "sendo", "será", "dos", "das", "artigo", "documento", "página");
    private final SearchTermProcessor terms;
    private final LegalTextNormalizer normalizer;

    public SearchContextService(SearchTermProcessor terms, LegalTextNormalizer normalizer) {
        this.terms = terms; this.normalizer = normalizer;
    }

    public PreparedContext prepare(String description, String documentText) {
        String text = normalizer.normalizePlainText(documentText);
        if (text == null) throw new DocumentUploadException(HttpStatus.UNPROCESSABLE_ENTITY, "O contexto do documento está vazio.");
        Map<String,Integer> frequency = new LinkedHashMap<>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}]+")) {
            if (token.length() >= 3 && token.length() <= 40 && !COMMON.contains(token)
                    && !terms.isStopWord(token)) {
                frequency.merge(token, 1, Integer::sum);
            }
        }
        List<String> ranked = new ArrayList<>(frequency.keySet());
        ranked.sort(Comparator.comparingInt((String token) -> frequency.get(token)).reversed());
        List<String> context = new ArrayList<>();
        int contextLength = 0;
        for (String token : ranked) {
            if (context.size() >= 18) break;
            if (contextLength + token.length() + (context.isEmpty() ? 0 : 1) > 140) continue;
            context.add(token); contextLength += token.length() + (context.size() == 1 ? 0 : 1);
        }
        if (context.isEmpty()) throw new DocumentUploadException(HttpStatus.UNPROCESSABLE_ENTITY, "Não foi possível identificar palavras para complementar a pesquisa. Revise o texto extraído.");
        var query = new LinkedHashSet<String>();
        int budget = 300 - contextLength - 1;
        for (String token : terms.tokenize(description)) {
            if (token.length() > budget) continue;
            query.add(token); budget -= token.length() + 1;
        }
        query.addAll(context);
        return new PreparedContext(String.join(" ", query), List.copyOf(context));
    }

    public record PreparedContext(String consulta, List<String> termosContexto) {}
}
