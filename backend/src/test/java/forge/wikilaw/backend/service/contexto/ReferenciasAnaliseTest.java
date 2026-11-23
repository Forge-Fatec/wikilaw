package forge.wikilaw.backend.service.contexto;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.dto.AnaliseJuridicaResponse.FonteAnalise;
import forge.wikilaw.backend.dto.PesquisaPdfRequest.DocumentoReferencia;
import forge.wikilaw.backend.service.contexto.ContextoJuridico.DocumentoContexto;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenciasAnaliseTest {

    /** Contexto com os resultados das posicoes 1, 3 e 5 da pesquisa. */
    private final ContextoJuridico contexto = new ContextoJuridico("dano moral", List.of(), List.of(
            documento(1, 10L, "https://stj.jus.br/10"), documento(3, 30L, null), documento(5, 50L, "https://stj.jus.br/50")),
            5, List.of());
    private final ReferenciasAnalise referencias = new ReferenciasAnalise(contexto);

    @Test
    void keepsCitationsOfDocumentsSentToTheModel() {
        assertThat(referencias.citar("Resumo", "O dano é presumido [1, 3].")).isEqualTo("O dano é presumido [1, 3].");
        assertThat(referencias.citar("Resumo", "Ver [5]")).isEqualTo("Ver [5]");
        assertThat(referencias.descartadas()).isZero();
    }

    @Test
    void dropsNumbersThatAreNotInTheContext() {
        assertThat(referencias.citar("Resumo", "Tese firmada [2, 3, 9].")).isEqualTo("Tese firmada [3].");
        assertThat(referencias.descartadas()).isEqualTo(2);
    }

    @Test
    void removesAMarkerWithoutAnyVerifiableSource() {
        assertThat(referencias.citar("Pontos principais", "Prazo de cinco anos [4].")).isEqualTo("Prazo de cinco anos.");
        assertThat(referencias.citar("Pontos principais", "[7] Sem fonte")).isEqualTo("Sem fonte");
        assertThat(referencias.citar("Pontos principais", "[8]")).isNull();
        assertThat(referencias.fontes()).isEmpty();
    }

    @Test
    void understandsRangesSeparatorsAndDocumentPrefixes() {
        assertThat(referencias.citar("Resumo", "A [1-5] B [doc. 3 e 5] C [1; 5]"))
                .isEqualTo("A [1, 3, 5] B [3, 5] C [1, 5]");
        assertThat(referencias.citar("Resumo", "D [1-900]")).isEqualTo("D [1]");
    }

    @Test
    void leavesBracketsThatAreNotCitations() {
        assertThat(referencias.citar("Resumo", "O autor [sic] e o art. 5º [CF/88]."))
                .isEqualTo("O autor [sic] e o art. 5º [CF/88].");
        assertThat(referencias.fontes()).isEmpty();
    }

    @Test
    void listsOnlyCitedSourcesWithTheSectionsTheySupport() {
        referencias.citar("Resumo", "Dano presumido [5, 1].");
        referencias.citar("Pontos principais", List.of("Inscrição indevida [1]", "Sem citação", "   "));
        referencias.registrar("Documentos mais relevantes", 5);
        referencias.registrar("Documentos mais relevantes", 2);

        assertThat(referencias.fontes()).containsExactly(
                new FonteAnalise(1, "decisoes", 10L, "Doc 1", "STJ", "STJ", "REsp 1", "ext-1", "https://stj.jus.br/10",
                        List.of("Resumo", "Pontos principais")),
                new FonteAnalise(5, "decisoes", 50L, "Doc 5", "STJ", "STJ", "REsp 5", "ext-5", "https://stj.jus.br/50",
                        List.of("Resumo", "Documentos mais relevantes")));
        assertThat(referencias.descartadas()).isEqualTo(1);
    }

    @Test
    void dropsBlankTextsFromLists() {
        assertThat(referencias.citar("Divergências", List.of("A [3]", "[9]"))).containsExactly("A [3]");
        assertThat(referencias.citar("Resumo", (String) null)).isNull();
    }

    private static DocumentoContexto documento(int numero, Long id, String url) {
        return new DocumentoContexto(numero, new DocumentoReferencia("decisoes", id), "Doc " + numero, List.of(), false,
                new FonteDocumento("STJ", "STJ", "REsp " + numero, "ext-" + numero, url));
    }
}
