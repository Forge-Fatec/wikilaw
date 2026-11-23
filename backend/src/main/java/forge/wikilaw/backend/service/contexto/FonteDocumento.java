package forge.wikilaw.backend.service.contexto;

import forge.wikilaw.backend.service.DocumentQueryService.Summary;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Identificacao de um documento do contexto na fonte de onde veio, para a analise apontar a origem de cada
 * informacao e o usuario abrir o registro juridico original.
 *
 * @param fonte sigla da fonte de dados (STJ, TJDFT, BDTD...)
 * @param tribunal sigla do tribunal, quando houver
 * @param numeroProcessoOuTema numero do processo, do tema ou do precedente
 * @param identificadorExterno identificador do documento na fonte
 * @param url endereco do documento original; so {@code http} e {@code https}, ou {@code null}
 */
public record FonteDocumento(
        String fonte,
        String tribunal,
        String numeroProcessoOuTema,
        String identificadorExterno,
        String url) {

    public static FonteDocumento de(Summary doc) {
        return new FonteDocumento(texto(doc.fonte()), texto(doc.tribunal()), texto(doc.numeroProcessoOuTema()),
                texto(doc.identificadorExterno()), urlDeAcesso(doc.urlOriginal()));
    }

    /** Aceita so links web absolutos, para a tela nunca abrir esquemas como {@code javascript:} vindos da fonte. */
    static String urlDeAcesso(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            URI uri = new URI(url.strip());
            String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!esquema.equals("http") && !esquema.equals("https")) return null;
            return uri.getHost() == null ? null : uri.toString();
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
