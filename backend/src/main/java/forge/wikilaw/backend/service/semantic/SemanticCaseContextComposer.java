package forge.wikilaw.backend.service.semantic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Monta o texto da consulta a partir das partes do caso, com rotulos no mesmo estilo dos trechos do acervo
 * ("Secao: ..."), para que fatos, fundamentos juridicos e pedidos pesem juntos no vetor da consulta.
 * A ordem e fixa (fatos, fundamentos, pedidos, descricao) e partes repetidas entram uma vez so.
 */
@Component
public class SemanticCaseContextComposer {
    static final String FATOS = "Fatos";
    static final String FUNDAMENTOS = "Fundamentos juridicos";
    static final String PEDIDOS = "Pedidos";
    static final String DESCRICAO = "Descricao do caso";

    private final LegalTextNormalizer normalizer;

    public SemanticCaseContextComposer(LegalTextNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    public CaseContext compose(SemanticCaseQuery query) {
        var sections = new ArrayList<String>();
        var seen = new LinkedHashSet<String>();
        var text = new StringBuilder();
        int informedCharacters = 0;
        for (var part : List.of(
                new Part(FATOS, query.fatos()),
                new Part(FUNDAMENTOS, query.fundamentos()),
                new Part(PEDIDOS, query.pedidos()),
                new Part(DESCRICAO, query.descricao()))) {
            String value = normalizer.normalize(part.value());
            if (value == null || !seen.add(value)) continue;
            if (!text.isEmpty()) text.append("\n\n");
            text.append(part.label()).append(":\n").append(value);
            sections.add(part.label());
            informedCharacters += value.length();
        }
        return new CaseContext(text.toString(), informedCharacters, List.copyOf(sections));
    }

    private record Part(String label, String value) {}

    /** Texto rotulado enviado ao provedor, quantos caracteres o usuario informou e quais partes entraram. */
    public record CaseContext(String texto, int caracteresInformados, List<String> secoes) {

        public boolean vazio() {
            return secoes.isEmpty();
        }
    }
}
