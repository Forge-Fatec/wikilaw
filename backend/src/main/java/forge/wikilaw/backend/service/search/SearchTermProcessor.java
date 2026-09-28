package forge.wikilaw.backend.service.search;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Normaliza termos de busca para os serviços de documentos jurídicos.
 *
 * <p>Este componente é compartilhado por jurisprudência, precedentes e doutrina
 * para que as categorias adotem as mesmas regras de tokenização e escape.
 */
@Component
public class SearchTermProcessor {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "o", "as", "os", "de", "da", "do", "das", "dos",
            "e", "em", "no", "na", "nos", "nas", "por", "para", "com",
            "sem", "um", "uma", "uns", "umas", "que");

    /**
     * Retorna palavras relevantes em minúsculas, sem conectivos ou repetições.
     * A ordem em que as palavras aparecem no termo original é preservada.
     */
    public List<String> tokenize(String term) {
        if (term == null || term.isBlank()) {
            return List.of();
        }

        String normalized = term.trim().toLowerCase(Locale.ROOT);
        var tokens = new LinkedHashSet<String>();
        for (String token : normalized.split("[^\\p{L}\\p{N}]+")) {
            if (!token.isBlank() && !STOP_WORDS.contains(token)) {
                tokens.add(token);
            }
        }

        // Mantém uma busca útil quando o texto contém somente conectivos.
        if (tokens.isEmpty()) {
            tokens.add(normalized);
        }
        return List.copyOf(tokens);
    }

    /** Cria um padrão de contenção e escapa metacaracteres de SQL LIKE. */
    public String containsPattern(String token) {
        return "%" + token
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
    }
}
