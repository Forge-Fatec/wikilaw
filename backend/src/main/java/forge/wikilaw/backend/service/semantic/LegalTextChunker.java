package forge.wikilaw.backend.service.semantic;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Divisao deterministica por limites naturais, usando quatro caracteres como estimativa conservadora de token. */
@Component
public class LegalTextChunker {
    private static final int CHARACTERS_PER_TOKEN = 4;
    private final int maxTokens;
    private final int overlapTokens;

    public LegalTextChunker(
            @Value("${wikilaw.semantic.chunk.max-tokens:600}") int maxTokens,
            @Value("${wikilaw.semantic.chunk.overlap-tokens:80}") int overlapTokens) {
        if (maxTokens < 50) throw new IllegalArgumentException("max-tokens deve ser pelo menos 50");
        if (overlapTokens < 0 || overlapTokens >= maxTokens / 2)
            throw new IllegalArgumentException("overlap-tokens deve ser menor que metade de max-tokens");
        this.maxTokens = maxTokens;
        this.overlapTokens = overlapTokens;
    }

    public List<String> split(String text) {
        if (text == null || text.isBlank()) return List.of();
        int maxCharacters = maxTokens * CHARACTERS_PER_TOKEN;
        int overlapCharacters = overlapTokens * CHARACTERS_PER_TOKEN;
        if (text.length() <= maxCharacters) return List.of(text.trim());

        var chunks = new ArrayList<String>();
        int start = 0;
        while (start < text.length()) {
            int targetEnd = Math.min(text.length(), start + maxCharacters);
            int end = targetEnd == text.length() ? targetEnd : naturalBoundary(text, start, targetEnd);
            if (end <= start) end = targetEnd;

            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank()) chunks.add(chunk);
            if (end >= text.length()) break;

            int next = Math.max(start + 1, end - overlapCharacters);
            int wordBoundary = text.lastIndexOf(' ', next);
            if (wordBoundary > start && next - wordBoundary < 40) next = wordBoundary + 1;
            start = next;
        }
        return List.copyOf(chunks);
    }

    public int estimateTokens(String text) {
        return Math.max(1, (text.length() + CHARACTERS_PER_TOKEN - 1) / CHARACTERS_PER_TOKEN);
    }

    public String configurationSignature() {
        return maxTokens + ":" + overlapTokens;
    }

    private int naturalBoundary(String text, int start, int targetEnd) {
        int minimum = start + (int) ((targetEnd - start) * 0.60);
        int paragraph = text.lastIndexOf('\n', targetEnd);
        if (paragraph >= minimum) return paragraph;

        for (int i = targetEnd - 1; i >= minimum; i--) {
            char current = text.charAt(i);
            if ((current == '.' || current == '!' || current == '?' || current == ';')
                    && (i + 1 >= text.length() || Character.isWhitespace(text.charAt(i + 1)))) {
                return i + 1;
            }
        }
        int whitespace = text.lastIndexOf(' ', targetEnd);
        return whitespace >= minimum ? whitespace : targetEnd;
    }
}

