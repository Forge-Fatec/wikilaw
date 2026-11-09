package forge.wikilaw.backend.service.contexto;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.dto.PesquisaPdfRequest.DocumentoReferencia;
import forge.wikilaw.backend.service.contexto.ContextoJuridico.Campo;
import forge.wikilaw.backend.service.contexto.ContextoJuridico.DocumentoContexto;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContextoJuridicoPromptTest {

    @Test
    void writesEachDocumentInsideANumberedBlock() {
        var contexto = new ContextoJuridico("dano moral", List.of(), List.of(
                documento(1, "decisoes", 7L, "REsp dano moral", false),
                documento(2, "precedentes", 3L, "Tema 1061", false)), 2, List.of());

        String prompt = ContextoJuridicoPrompt.formatar(contexto);

        assertThat(prompt).startsWith("Pesquisa do usuário: dano moral\n")
                .contains("Documentos encontrados (2), numerados pela posição na lista de resultados:")
                .containsSubsequence("<documento numero=\"1\">", "Título: REsp dano moral", "</documento>",
                        "<documento numero=\"2\">", "Título: Tema 1061", "</documento>")
                .doesNotContain("limite de contexto", "<pesquisa_ativa>");
    }

    @Test
    void describesTheActiveSearchBeforeTheDocuments() {
        var contexto = new ContextoJuridico("dano moral", List.of(new Campo("Tribunal", "STJ"),
                new Campo("Ordenação", "Mais recentes primeiro")),
                List.of(documento(4, "decisoes", 7L, "REsp", false)), 30, List.of());

        String prompt = ContextoJuridicoPrompt.formatar(contexto);

        assertThat(prompt).containsSubsequence("Pesquisa do usuário: dano moral", "<pesquisa_ativa>", "Tribunal: STJ",
                "Ordenação: Mais recentes primeiro", "</pesquisa_ativa>", "Apenas 1 dos 30", "<documento numero=\"4\">");
    }

    @Test
    void tellsTheModelWhenResultsWereLeftOutOrShortened() {
        var omitido = new DocumentoReferencia("decisoes", 9L);
        var contexto = new ContextoJuridico(null, List.of(), List.of(documento(1, "decisoes", 7L, "REsp", true)), 2,
                List.of(omitido));

        String prompt = ContextoJuridicoPrompt.formatar(contexto);

        assertThat(prompt).contains("(sem termo informado)")
                .contains("Apenas 1 dos 2 resultados da pesquisa (os mais relevantes)")
                .contains("(Conteúdo resumido para caber no limite de contexto.)");
    }

    private static DocumentoContexto documento(int numero, String categoria, Long id, String titulo, boolean truncado) {
        return new DocumentoContexto(numero, new DocumentoReferencia(categoria, id), titulo,
                List.of(new Campo("Título", titulo)), truncado);
    }
}
