package forge.wikilaw.backend.service.semantic;

import forge.wikilaw.backend.entity.DocumentoSemantico;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PreparedLegalDocument(
        DocumentoSemantico.Categoria categoria,
        String titulo,
        Map<String, Object> metadados,
        List<Section> secoes) {

    public PreparedLegalDocument {
        metadados = Collections.unmodifiableMap(new LinkedHashMap<>(metadados));
        secoes = List.copyOf(secoes);
    }

    public record Section(String nome, String texto) {}
}

