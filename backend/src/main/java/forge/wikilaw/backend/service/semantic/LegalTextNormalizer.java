package forge.wikilaw.backend.service.semantic;

import java.text.Normalizer;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

@Component
public class LegalTextNormalizer {

    public String normalize(String value) {
        if (value == null || value.isBlank()) return null;

        var document = Jsoup.parse(value);
        document.select("br").before("\n");
        document.select("p,li,div,h1,h2,h3,h4,h5,h6,blockquote").after("\n");
        return normalizePlainText(document.body().wholeText());
    }

    /** Uploaded documents are already plain text; angle brackets are content, not HTML. */
    public String normalizePlainText(String value) {
        if (value == null || value.isBlank()) return null;
        String plain = Normalizer.normalize(value, Normalizer.Form.NFC)
                .replace('\u00a0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\p{Cc}&&[^\\n\\t]]", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return plain.isBlank() ? null : plain;
    }
}

