package forge.wikilaw.backend.service;

import static org.assertj.core.api.Assertions.*;
import forge.wikilaw.backend.service.search.SearchTermProcessor;
import forge.wikilaw.backend.service.semantic.LegalTextNormalizer;
import org.junit.jupiter.api.Test;

class SearchContextServiceTest {
    private final SearchContextService service = new SearchContextService(new SearchTermProcessor(), new LegalTextNormalizer());

    @Test void preservesBothCaseAndContextWithinTheExistingSearchBudget() {
        var result = service.prepare("atendimento urgente", "Indenização por negativação indevida e proteção do consumidor. Negativação indevida.");
        assertThat(result.consulta()).contains("atendimento", "urgente", "negativação", "consumidor");
        assertThat(result.termosContexto()).startsWith("negativação", "indevida");
        assertThat(result.termosContexto()).doesNotContain("por", "do");
    }
    @Test void usesTheWholeContextRatherThanOnlyItsFirstCharacters() {
        var result = service.prepare("caso", "este documento para este caso ".repeat(250) + " usucapião extraordinária posse usucapião");
        assertThat(result.consulta()).contains("usucapião").hasSizeLessThanOrEqualTo(300);
    }
    @Test void longDescriptionsAndContextsStayBoundedAndDeterministic() {
        String description = "responsabilidade civil indenização prejuízo consumidor contrato prazo ".repeat(4);
        String context = "proteção restituição negativação reparação recusa atendimento posse usucapião seguradora transporte escola criança ".repeat(10);
        assertThat(service.prepare(description, context)).isEqualTo(service.prepare(description, context));
        assertThat(service.prepare(description, context).consulta()).hasSizeLessThanOrEqualTo(300);
    }
    @Test void supportsDocumentOnlySearchesAndPreservesLiteralAngleBrackets() {
        assertThat(service.prepare(null, "Indenização consumidor").consulta()).contains("indenização", "consumidor");
        assertThat(new LegalTextNormalizer().normalizePlainText("Cláusula <exemplo> e valor < 10")).contains("<exemplo>", "< 10");
    }
}
