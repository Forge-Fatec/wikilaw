package forge.wikilaw.backend.service.semantic;

import forge.wikilaw.backend.config.SemanticSearchProperties;
import forge.wikilaw.backend.exception.SemanticQueryException;
import forge.wikilaw.backend.integration.embedding.EmbeddingProvider;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Transforma a descricao do caso pesquisado no vetor de consulta. O texto passa pela mesma normalizacao
 * do acervo e vai ao provedor como consulta ({@code input_type=query}), para cair no mesmo espaco dos trechos.
 */
@Service
public class SemanticCaseRepresentationService {
    private final LegalTextNormalizer normalizer;
    private final SemanticCaseContextComposer composer;
    private final EmbeddingProvider provider;
    private final SemanticSearchProperties properties;
    private final QueryVectorCache cache;

    public SemanticCaseRepresentationService(LegalTextNormalizer normalizer, SemanticCaseContextComposer composer,
            EmbeddingProvider provider, SemanticSearchProperties properties) {
        this.normalizer = normalizer;
        this.composer = composer;
        this.provider = provider;
        this.properties = properties;
        this.cache = new QueryVectorCache(properties.getQuery().getCacheSize());
    }

    public CaseRepresentation representar(String descricao) {
        return embed(normalize(descricao));
    }

    /**
     * Caso informado em partes: fatos, fundamentos juridicos e pedidos entram rotulados no mesmo texto,
     * de modo que o vetor represente o caso inteiro e nao so a parte mais longa.
     */
    public CaseRepresentation representar(SemanticCaseQuery query) {
        var limits = properties.getQuery();
        int rawLength = length(query.descricao()) + length(query.fatos())
                + length(query.fundamentos()) + length(query.pedidos());
        if (rawLength > limits.getMaxCharacters() * 4) throw tooLong(limits.getMaxCharacters());

        var context = composer.compose(query);
        if (context.vazio())
            throw new SemanticQueryException("Informe os fatos, os fundamentos, os pedidos ou a descricao do caso");
        if (context.caracteresInformados() < limits.getMinCharacters())
            throw new SemanticQueryException("Descreva o caso com pelo menos "
                    + limits.getMinCharacters() + " caracteres");
        if (context.texto().length() > limits.getMaxCharacters()) throw tooLong(limits.getMaxCharacters());
        return embed(context.texto());
    }

    private CaseRepresentation embed(String text) {
        String model = provider.model();
        String key = model + '\u0000' + text;
        float[] cached = cache.get(key);
        if (cached == null) {
            cached = provider.embedQuery(text);
            cache.put(key, cached);
        }
        return new CaseRepresentation(text, cached.clone(), model);
    }

    String normalize(String descricao) {
        var limits = properties.getQuery();
        // Antes de normalizar, barra entradas absurdas sem gastar tempo com o parser de HTML.
        if (descricao != null && descricao.length() > limits.getMaxCharacters() * 4)
            throw tooLong(limits.getMaxCharacters());
        String text = normalizer.normalize(descricao);
        if (text == null)
            throw new SemanticQueryException("Descreva o caso a ser pesquisado");
        if (text.length() < limits.getMinCharacters())
            throw new SemanticQueryException("Descreva o caso com pelo menos "
                    + limits.getMinCharacters() + " caracteres");
        if (text.length() > limits.getMaxCharacters())
            throw tooLong(limits.getMaxCharacters());
        return text;
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private SemanticQueryException tooLong(int maxCharacters) {
        return new SemanticQueryException("A descricao do caso deve ter no maximo " + maxCharacters + " caracteres");
    }

    /**
     * Cache LRU em memoria: a mesma descricao (pesquisa repetida, troca de pagina ou de filtro) nao gasta
     * outra requisicao do provedor. A chave inclui o modelo, entao trocar de modelo nunca reaproveita vetor antigo.
     */
    private static final class QueryVectorCache {
        private final int capacity;
        private final Map<String, float[]> entries;

        QueryVectorCache(int capacity) {
            this.capacity = capacity;
            this.entries = new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                    return size() > QueryVectorCache.this.capacity;
                }
            };
        }

        synchronized float[] get(String key) {
            return capacity == 0 ? null : entries.get(key);
        }

        synchronized void put(String key, float[] vector) {
            if (capacity > 0) entries.put(key, vector.clone());
        }
    }

    /** Texto efetivamente enviado ao provedor, o vetor resultante e o modelo que o gerou. */
    public record CaseRepresentation(String texto, float[] vetor, String modelo) {}
}
