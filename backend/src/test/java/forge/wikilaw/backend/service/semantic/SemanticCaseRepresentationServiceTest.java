package forge.wikilaw.backend.service.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import forge.wikilaw.backend.config.SemanticSearchProperties;
import forge.wikilaw.backend.exception.SemanticQueryException;
import forge.wikilaw.backend.integration.embedding.EmbeddingProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SemanticCaseRepresentationServiceTest {
    private EmbeddingProvider provider;
    private SemanticSearchProperties properties;
    private SemanticCaseRepresentationService service;

    @BeforeEach
    void setUp() {
        provider = mock(EmbeddingProvider.class);
        when(provider.model()).thenReturn("voyage-4");
        properties = new SemanticSearchProperties();
        service = new SemanticCaseRepresentationService(new LegalTextNormalizer(), provider, properties);
    }

    @Test
    void embedsTheNormalizedDescriptionAsAQuery() {
        float[] vector = {0.6f, 0.8f};
        when(provider.embedQuery("Meu vizinho bateu no meu carro\ne se recusa a pagar o conserto."))
                .thenReturn(vector);

        var result = service.representar(
                "<p>Meu   vizinho bateu no meu carro</p>  e se recusa a pagar o conserto.  ");

        assertThat(result.texto()).isEqualTo("Meu vizinho bateu no meu carro\ne se recusa a pagar o conserto.");
        assertThat(result.vetor()).isSameAs(vector);
        assertThat(result.modelo()).isEqualTo("voyage-4");
    }

    @Test
    void rejectsEmptyDescriptionsWithoutCallingTheProvider() {
        assertThatThrownBy(() -> service.representar("   "))
                .isInstanceOf(SemanticQueryException.class)
                .hasMessageContaining("Descreva o caso");
        assertThatThrownBy(() -> service.representar(null))
                .isInstanceOf(SemanticQueryException.class);
        verify(provider, never()).embedQuery(anyString());
    }

    @Test
    void rejectsDescriptionsTooShortToCarryContext() {
        assertThatThrownBy(() -> service.representar("dano moral"))
                .isInstanceOf(SemanticQueryException.class)
                .hasMessageContaining("pelo menos 20 caracteres");
        verify(provider, never()).embedQuery(anyString());
    }

    @Test
    void rejectsDescriptionsAboveTheConfiguredLimit() {
        properties.getQuery().setMaxCharacters(100);

        assertThatThrownBy(() -> service.representar("a".repeat(101)))
                .isInstanceOf(SemanticQueryException.class)
                .hasMessageContaining("no maximo 100 caracteres");
        assertThatThrownBy(() -> service.representar("a".repeat(401)))
                .isInstanceOf(SemanticQueryException.class);
        verify(provider, never()).embedQuery(anyString());
    }
}
