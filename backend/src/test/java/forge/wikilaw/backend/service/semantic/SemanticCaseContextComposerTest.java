package forge.wikilaw.backend.service.semantic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SemanticCaseContextComposerTest {
    private final SemanticCaseContextComposer composer = new SemanticCaseContextComposer(new LegalTextNormalizer());

    @Test
    void labelsFactsLegalGroundsAndRequestsInAFixedOrder() {
        var context = composer.compose(new SemanticCaseQuery(
                null,
                "Meu nome foi incluído no SPC por uma dívida que já paguei.",
                "Art. 14 do CDC: responsabilidade objetiva do fornecedor.",
                "Retirada do nome do cadastro e indenização por dano moral."));

        assertThat(context.texto()).isEqualTo("""
                Fatos:
                Meu nome foi incluído no SPC por uma dívida que já paguei.

                Fundamentos juridicos:
                Art. 14 do CDC: responsabilidade objetiva do fornecedor.

                Pedidos:
                Retirada do nome do cadastro e indenização por dano moral.""");
        assertThat(context.secoes()).containsExactly("Fatos", "Fundamentos juridicos", "Pedidos");
        assertThat(context.caracteresInformados()).isEqualTo(58 + 56 + 58);
    }

    @Test
    void skipsBlankPartsAndKeepsTheFreeDescriptionLast() {
        var context = composer.compose(new SemanticCaseQuery(
                "Quero saber se cabe indenização.", "<p>O banco cobrou tarifa   indevida.</p>", "  ", null));

        assertThat(context.texto()).isEqualTo("""
                Fatos:
                O banco cobrou tarifa indevida.

                Descricao do caso:
                Quero saber se cabe indenização.""");
        assertThat(context.secoes()).containsExactly("Fatos", "Descricao do caso");
    }

    @Test
    void includesRepeatedTextOnlyOnce() {
        var context = composer.compose(new SemanticCaseQuery(
                "Cobrança indevida de tarifa bancária.", "Cobrança indevida de tarifa bancária.", null, null));

        assertThat(context.secoes()).containsExactly("Fatos");
    }

    @Test
    void reportsEmptyWhenNothingWasInformed() {
        var context = composer.compose(new SemanticCaseQuery(" ", null, "", "<p></p>"));

        assertThat(context.vazio()).isTrue();
        assertThat(context.texto()).isEmpty();
        assertThat(context.caracteresInformados()).isZero();
    }
}
