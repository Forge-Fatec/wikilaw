package forge.wikilaw.backend.service;

import forge.wikilaw.backend.repository.FonteDadosRepository;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Siglas de fonte vindas da tela (o parâmetro fonte pode se repetir). Siglas em
 * branco ou repetidas são descartadas; lista vazia significa todas as fontes.
 */
final class FiltroFontes {

    private FiltroFontes() {
    }

    static List<String> uma(String sigla) {
        return sigla == null ? List.of() : normalizar(List.of(sigla));
    }

    static List<String> normalizar(Collection<String> siglas) {
        if (siglas == null) {
            return List.of();
        }
        return siglas.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(sigla -> !sigla.isEmpty())
                .map(sigla -> sigla.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    /** Ids das fontes; sigla fora do cadastro é erro do cliente. */
    static List<Long> ids(FonteDadosRepository fontes, Collection<String> siglas) {
        return normalizar(siglas).stream()
                .map(sigla -> fontes.findBySigla(sigla)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fonte desconhecida"))
                        .getId())
                .toList();
    }

    /** Ids só das siglas cadastradas; as demais são ignoradas. */
    static List<Long> idsConhecidos(FonteDadosRepository fontes, Collection<String> siglas) {
        return normalizar(siglas).stream()
                .flatMap(sigla -> fontes.findBySigla(sigla).stream())
                .map(fonte -> fonte.getId())
                .toList();
    }
}
