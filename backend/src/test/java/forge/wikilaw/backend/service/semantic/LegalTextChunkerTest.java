package forge.wikilaw.backend.service.semantic;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegalTextChunkerTest {
    @Test
    void splitsLongTextAtNaturalBoundariesWithinConfiguredLimit() {
        var chunker = new LegalTextChunker(50, 5);
        String text = IntStream.range(0, 80)
                .mapToObj(index -> "Paragrafo " + index + " trata de responsabilidade civil.")
                .collect(java.util.stream.Collectors.joining("\n"));

        var chunks = chunker.split(text);

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().noneMatch(String::isBlank));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.length() <= 200));
        assertTrue(chunks.stream().allMatch(chunk -> chunker.estimateTokens(chunk) <= 50));
    }

    @Test
    void rejectsOverlapThatCouldPreventProgress() {
        assertThrows(IllegalArgumentException.class, () -> new LegalTextChunker(100, 50));
    }
}

