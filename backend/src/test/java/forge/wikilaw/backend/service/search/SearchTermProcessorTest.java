package forge.wikilaw.backend.service.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SearchTermProcessorTest {

    private final SearchTermProcessor processor = new SearchTermProcessor();

    @Test
    void normalizesRemovesStopWordsAndDuplicates() {
        assertEquals(
                List.of("dano", "moral", "consumidor"),
                processor.tokenize("Dano moral do consumidor, dano!"));
    }

    @Test
    void preservesAccentedLettersAndNumbers() {
        assertEquals(
                List.of("negativação", "indevida", "123"),
                processor.tokenize("Negativação indevida 123"));
    }

    @Test
    void fallsBackToOriginalTextWhenOnlyStopWordsArePresent() {
        assertEquals(List.of("de e com"), processor.tokenize(" de e com "));
    }

    @Test
    void returnsNoTokensForBlankInput() {
        assertEquals(List.of(), processor.tokenize("  "));
    }

    @Test
    void escapesLikeMetacharacters() {
        assertEquals("%100\\%\\_\\\\%", processor.containsPattern("100%_\\"));
    }
}
