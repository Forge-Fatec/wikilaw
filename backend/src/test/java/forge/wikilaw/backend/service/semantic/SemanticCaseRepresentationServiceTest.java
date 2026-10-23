package forge.wikilaw.backend.service.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import forge.wikilaw.backend.config.SemanticSearchProperties;
import forge.wikilaw.backend.exception.SemanticQueryException;
import forge.wikilaw.backend.integration.embedding.EmbeddingProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SemanticCaseRepresentationServiceTest {
    private EmbeddingProvider provider;
    private SemanticSearchProperties properties;
    private SemanticCaseRepresentationService service;

    @BeforeEach
    void setUp() {
        provider = mock(EmbeddingProvider.class);
        when(provider.model()).thenReturn("voyage-4");
        properties = new SemanticSearchProperties();
        service = newService();
    }

    private SemanticCaseRepresentationService newService() {
        var normalizer = new LegalTextNormalizer();
        return new SemanticCaseRepresentationService(normalizer, new SemanticCaseContextComposer(normalizer),
                provider, properties);
    }

    @Test
    void embedsFactsLegalGroundsAndRequestsTogether() {
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        when(provider.embedQuery(sent.capture())).thenReturn(new float[] {1f});

        var result = service.representar(new SemanticCaseQuery(null,
                "Meu nome foi incluído no SPC por uma dívida já paga.",
                "Art. 14 do CDC.",
                "Indenização por dano moral."));

        assertThat(sent.getValue())
                .contains("Fatos:\nMeu nome foi incluído no SPC por uma dívida já paga.")
                .contains("Fundamentos juridicos:\nArt. 14 do CDC.")
                .contains("Pedidos:\nIndenização por dano moral.");
        assertThat(result.texto()).isEqualTo(sent.getValue());
    }

    @Test
    void acceptsAStructuredCaseWithOnlyOnePart() {
        when(provider.embedQuery(anyString())).thenReturn(new float[] {1f});

        var result = service.representar(new SemanticCaseQuery(null, null, null,
                "Revisão do contrato de financiamento imobiliário."));

        assertThat(result.texto()).startsWith("Pedidos:\n");
    }

    @Test
    void rejectsStructuredCasesWithoutContent() {
        assertThatThrownBy(() -> service.representar(new SemanticCaseQuery(" ", "", null, "<p></p>")))
                .isInstanceOf(SemanticQueryException.class)
                .hasMessageContaining("fatos, os fundamentos, os pedidos");
        assertThatThrownBy(() -> service.representar(new SemanticCaseQuery(null, "Batida", "CC", null)))
                .isInstanceOf(SemanticQueryException.class)
                .hasMessageContaining("pelo menos 20 caracteres");
        verify(provider, never()).embedQuery(anyString());
    }

    @Test
    void appliesTheMaximumToTheComposedContext() {
        properties.getQuery().setMaxCharacters(100);
        service = newService();

        assertThatThrownBy(() -> service.representar(new SemanticCaseQuery(null,
                "f".repeat(40), "g".repeat(40), "p".repeat(40))))
                .isInstanceOf(SemanticQueryException.class)
                .hasMessageContaining("no maximo 100 caracteres");
        verify(provider, never()).embedQuery(anyString());
    }

    @Test
    void sharesTheCacheBetweenEquivalentStructuredQueries() {
        when(provider.embedQuery(anyString())).thenReturn(new float[] {1f});
        var query = new SemanticCaseQuery(null, "Cobrança indevida de tarifa.", null, "Devolução em dobro.");

        service.representar(query);
        service.representar(new SemanticCaseQuery(null, " Cobrança  indevida de tarifa. ", "", "Devolução em dobro."));

        verify(provider, times(1)).embedQuery(anyString());
    }

    @Test
    void embedsTheNormalizedDescriptionAsAQuery() {
        float[] vector = {0.6f, 0.8f};
        when(provider.embedQuery("Meu vizinho bateu no meu carro\ne se recusa a pagar o conserto."))
                .thenReturn(vector);

        var result = service.representar(
                "<p>Meu   vizinho bateu no meu carro</p>  e se recusa a pagar o conserto.  ");

        assertThat(result.texto()).isEqualTo("Meu vizinho bateu no meu carro\ne se recusa a pagar o conserto.");
        assertThat(result.vetor()).containsExactly(vector);
        assertThat(result.modelo()).isEqualTo("voyage-4");
    }

    @Test
    void reusesTheVectorOfARepeatedDescription() {
        when(provider.embedQuery(anyString())).thenReturn(new float[] {1f, 0f});

        var first = service.representar("Cobrança indevida de tarifa bancária em conta salário");
        first.vetor()[0] = 99f;
        var second = service.representar("  Cobrança indevida de tarifa bancária   em conta salário ");

        verify(provider, times(1)).embedQuery(anyString());
        assertThat(second.vetor()).containsExactly(1f, 0f);
    }

    @Test
    void neverReusesVectorsAcrossModels() {
        when(provider.embedQuery(anyString())).thenReturn(new float[] {1f});
        service.representar("Cobrança indevida de tarifa bancária em conta salário");

        when(provider.model()).thenReturn("voyage-law-2");
        var result = service.representar("Cobrança indevida de tarifa bancária em conta salário");

        verify(provider, times(2)).embedQuery(anyString());
        assertThat(result.modelo()).isEqualTo("voyage-law-2");
    }

    @Test
    void evictsTheLeastRecentlyUsedDescriptionWhenFull() {
        properties.getQuery().setCacheSize(1);
        service = newService();
        when(provider.embedQuery(anyString())).thenReturn(new float[] {1f});

        service.representar("Primeiro caso de negativação indevida");
        service.representar("Segundo caso sobre usucapião extraordinária");
        service.representar("Primeiro caso de negativação indevida");

        verify(provider, times(3)).embedQuery(anyString());
    }

    @Test
    void callsTheProviderEveryTimeWhenCacheIsDisabled() {
        properties.getQuery().setCacheSize(0);
        service = newService();
        when(provider.embedQuery(anyString())).thenReturn(new float[] {1f});

        service.representar("Cobrança indevida de tarifa bancária em conta salário");
        service.representar("Cobrança indevida de tarifa bancária em conta salário");

        verify(provider, times(2)).embedQuery(anyString());
    }

    @Test
    void rejectsEmptyDescriptionsWithoutCallingTheProvider() {
        assertThatThrownBy(() -> service.representar("   "))
                .isInstanceOf(SemanticQueryException.class)
                .hasMessageContaining("Descreva o caso");
        assertThatThrownBy(() -> service.representar((String) null))
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
