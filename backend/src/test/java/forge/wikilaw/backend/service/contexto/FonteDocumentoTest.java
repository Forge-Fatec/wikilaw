package forge.wikilaw.backend.service.contexto;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.service.DocumentQueryService.Summary;
import org.junit.jupiter.api.Test;

class FonteDocumentoTest {

    @Test
    void keepsTheIdentifiersOfTheOriginalSource() {
        var doc = new Summary(7L, "STJ", "decisoes", " 2024/0012345-6 ", "REsp 1", null, "REsp 1.234.567/SP", null,
                null, null, null, "STJ", null, null, null, null, null, null, null, null, null, null, null,
                " https://processo.stj.jus.br/processo/123 ", null);

        assertThat(FonteDocumento.de(doc)).isEqualTo(new FonteDocumento("STJ", "STJ", "REsp 1.234.567/SP",
                "2024/0012345-6", "https://processo.stj.jus.br/processo/123"));
    }

    @Test
    void acceptsOnlyAbsoluteWebLinks() {
        assertThat(FonteDocumento.urlDeAcesso("http://www.tjdft.jus.br/a?b=1")).isEqualTo("http://www.tjdft.jus.br/a?b=1");
        assertThat(FonteDocumento.urlDeAcesso("HTTPS://scielo.br/x")).isEqualTo("HTTPS://scielo.br/x");
        assertThat(FonteDocumento.urlDeAcesso("javascript:alert(1)")).isNull();
        assertThat(FonteDocumento.urlDeAcesso("ftp://arquivos.stj.jus.br/x")).isNull();
        assertThat(FonteDocumento.urlDeAcesso("/processo/123")).isNull();
        assertThat(FonteDocumento.urlDeAcesso("https://exemplo .com")).isNull();
        assertThat(FonteDocumento.urlDeAcesso("  ")).isNull();
        assertThat(FonteDocumento.urlDeAcesso(null)).isNull();
    }

    @Test
    void leavesMissingIdentifiersEmpty() {
        var doc = new Summary(1L, " ", "doutrina", null, "Artigo", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null);

        assertThat(FonteDocumento.de(doc)).isEqualTo(new FonteDocumento(null, null, null, null, null));
    }
}
