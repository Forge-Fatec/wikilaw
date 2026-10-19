package forge.wikilaw.backend.service.semantic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegalTextNormalizerTest {
    private final LegalTextNormalizer normalizer = new LegalTextNormalizer();

    @Test
    void removesMarkupButPreservesLegalMeaningAndParagraphs() {
        String result = normalizer.normalize("<p>Art. 5º — não cabe indenização.</p><p>Lei 8.078/90</p>");

        assertFalse(result.contains("<p>"));
        assertTrue(result.contains("Art. 5º — não cabe indenização."));
        assertTrue(result.contains("Lei 8.078/90"));
        assertTrue(result.contains("\n"));
    }

    @Test
    void blankMarkupBecomesNull() {
        assertNull(normalizer.normalize("<div> &nbsp; </div>"));
    }
}

