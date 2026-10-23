package forge.wikilaw.backend.service.semantic;

import forge.wikilaw.backend.config.SemanticSearchProperties;
import forge.wikilaw.backend.exception.SemanticQueryException;
import forge.wikilaw.backend.integration.embedding.EmbeddingProvider;
import org.springframework.stereotype.Service;

/**
 * Transforma a descricao do caso pesquisado no vetor de consulta. O texto passa pela mesma normalizacao
 * do acervo e vai ao provedor como consulta ({@code input_type=query}), para cair no mesmo espaco dos trechos.
 */
@Service
public class SemanticCaseRepresentationService {
    private final LegalTextNormalizer normalizer;
    private final EmbeddingProvider provider;
    private final SemanticSearchProperties properties;

    public SemanticCaseRepresentationService(LegalTextNormalizer normalizer, EmbeddingProvider provider,
            SemanticSearchProperties properties) {
        this.normalizer = normalizer;
        this.provider = provider;
        this.properties = properties;
    }

    public CaseRepresentation representar(String descricao) {
        String text = normalize(descricao);
        return new CaseRepresentation(text, provider.embedQuery(text), provider.model());
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

    private SemanticQueryException tooLong(int maxCharacters) {
        return new SemanticQueryException("A descricao do caso deve ter no maximo " + maxCharacters + " caracteres");
    }

    /** Texto efetivamente enviado ao provedor, o vetor resultante e o modelo que o gerou. */
    public record CaseRepresentation(String texto, float[] vetor, String modelo) {}
}
