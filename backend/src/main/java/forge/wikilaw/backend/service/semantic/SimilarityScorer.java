package forge.wikilaw.backend.service.semantic;

import forge.wikilaw.backend.config.SemanticSearchProperties;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Percentual de similaridade e justificativa exibidos em cada resultado da busca semantica. */
@Component
public class SimilarityScorer {
    private static final Map<String, String> SECTIONS = Map.of(
            "EMENTA", "A ementa",
            "DECISAO", "A decisão",
            "INTEIRO_TEOR", "O inteiro teor",
            "QUESTAO_JURIDICA", "A questão jurídica",
            "TESE", "A tese",
            "RESUMO", "O resumo",
            "PALAVRAS_CHAVE", "As palavras-chave",
            "TITULO", "O título");

    private final double minimum;
    private final double maximum;

    public SimilarityScorer(SemanticSearchProperties properties) {
        this.minimum = properties.getScore().getMinimum();
        this.maximum = properties.getScore().getMaximum();
    }

    /** Escala linear: minimum vale 0% e maximum vale 100%; fora da faixa, o valor fica no limite. */
    public int percentual(double similaridade) {
        double scaled = (similaridade - minimum) / (maximum - minimum);
        return (int) Math.round(Math.clamp(scaled, 0.0, 1.0) * 100);
    }

    public String justificativa(String secao, int trechosSemelhantes) {
        String parte = SECTIONS.getOrDefault(secao, "O conteúdo");
        String verbo = parte.startsWith("As ") ? "se aproximam" : "se aproxima";
        String quantidade = trechosSemelhantes > 1 ? " (" + trechosSemelhantes + " trechos semelhantes)" : "";
        return parte + " deste documento " + verbo + " do caso descrito" + quantidade + ".";
    }
}