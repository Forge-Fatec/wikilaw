package forge.wikilaw.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmbeddingTrechoRepositoryTest {

    @Test
    void formatsVectorAsPgvectorLiteral() {
        float[] vector = new float[EmbeddingTrechoRepository.DIMENSIONS];
        vector[0] = 0.5f;
        vector[1] = -0.25f;
        vector[EmbeddingTrechoRepository.DIMENSIONS - 1] = 1.0f;

        String literal = EmbeddingTrechoRepository.toVectorLiteral(vector);

        assertThat(literal).startsWith("[0.5,-0.25,0.0,").endsWith(",1.0]");
        assertThat(literal.split(",")).hasSize(EmbeddingTrechoRepository.DIMENSIONS);
    }

    @Test
    void rejectsVectorsWithWrongDimensions() {
        assertThatThrownBy(() -> EmbeddingTrechoRepository.toVectorLiteral(new float[1536]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1024");
        assertThatThrownBy(() -> EmbeddingTrechoRepository.toVectorLiteral(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonFiniteValues() {
        float[] vector = new float[EmbeddingTrechoRepository.DIMENSIONS];
        vector[3] = Float.NaN;

        assertThatThrownBy(() -> EmbeddingTrechoRepository.toVectorLiteral(vector))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("posicao 3");
    }
}
