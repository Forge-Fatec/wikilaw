package forge.wikilaw.backend.service.semantic;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.config.SemanticSearchProperties;
import org.junit.jupiter.api.Test;

class SimilarityScorerTest {
    private final SimilarityScorer scorer = new SimilarityScorer(new SemanticSearchProperties());

    @Test
    void scalesSimilarityBetweenTheConfiguredMinimumAndMaximum() {
        assertThat(scorer.percentual(0.2)).isZero();
        assertThat(scorer.percentual(0.45)).isEqualTo(50);
        assertThat(scorer.percentual(0.7)).isEqualTo(100);
        assertThat(scorer.percentual(0.588)).isEqualTo(78);
    }

    @Test
    void keepsSimilarityOutsideTheRangeWithinZeroAndOneHundred() {
        assertThat(scorer.percentual(0.05)).isZero();
        assertThat(scorer.percentual(0.95)).isEqualTo(100);
    }

    @Test
    void usesTheConfiguredRange() {
        var properties = new SemanticSearchProperties();
        properties.getScore().setMinimum(0.0);
        properties.getScore().setMaximum(1.0);

        assertThat(new SimilarityScorer(properties).percentual(0.588)).isEqualTo(59);
    }

    @Test
    void explainsWhichPartOfTheDocumentMatchedTheCase() {
        assertThat(scorer.justificativa("TESE", 1))
                .isEqualTo("A tese deste documento se aproxima do caso descrito.");
        assertThat(scorer.justificativa("PALAVRAS_CHAVE", 3))
                .isEqualTo("As palavras-chave deste documento se aproximam do caso descrito (3 trechos semelhantes).");
        assertThat(scorer.justificativa("SECAO_NOVA", 1))
                .isEqualTo("O conteúdo deste documento se aproxima do caso descrito.");
    }
}