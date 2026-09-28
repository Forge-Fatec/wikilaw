package forge.wikilaw.backend.integration.documents;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.jsoup.Jsoup;
import tools.jackson.databind.JsonNode;

public final class DocumentFields {
    private DocumentFields() {}
    public static String value(JsonNode n, String key) {
        JsonNode v = n.path(key);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }
    public static String required(JsonNode n, String key) {
        String s = value(n, key);
        if (s == null || s.isBlank()) throw new IllegalArgumentException("Registro sem " + key);
        return s;
    }
    public static String plain(String s) { return s == null ? null : Jsoup.parse(s).text(); }
    public static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    public static List<JsonNode> array(JsonNode n, String description) {
        if (!n.isArray()) throw new IllegalArgumentException("Resposta incompatível: " + description);
        List<JsonNode> values = new ArrayList<>();
        n.forEach(values::add);
        return values;
    }
    public static LocalDate date(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            if (s.matches("[0-9]{8}")) return LocalDate.parse(s, DateTimeFormatter.BASIC_ISO_DATE);
            if (s.matches("[0-9]{2}/[0-9]{2}/[0-9]{4}")) return LocalDate.parse(s, DateTimeFormatter.ofPattern("dd/MM/uuuu"));
            if (s.length() >= 10) return LocalDate.parse(s.substring(0, 10));
        } catch (java.time.DateTimeException ignored) { /* Data parcial permanece em dataOriginal. */ }
        return null;
    }
    public static String join(Collection<String> values) {
        return values.stream().filter(Objects::nonNull).filter(s -> !s.isBlank()).distinct()
            .collect(java.util.stream.Collectors.joining("; "));
    }
}
