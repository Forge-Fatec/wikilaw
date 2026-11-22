package forge.wikilaw.backend.service.contexto;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.dto.PesquisaAtivaRequest;
import forge.wikilaw.backend.service.contexto.ContextoJuridico.Campo;
import forge.wikilaw.backend.service.pesquisa.PesquisaAtiva;
import java.util.List;
import org.junit.jupiter.api.Test;

class DescricaoPesquisaMutationTest {

    @Test
    void keepsContextualQueryAtTheExactCharacterLimit() {
        String consultaNoLimite = "a".repeat(1_000);
        var filtros = new PesquisaAtivaRequest(null, consultaNoLimite, null, null, null, null, null,
                null, null, null, null, null);

        List<Campo> campos = DescricaoPesquisa.campos(new PesquisaAtiva(null, filtros, List.of(), 1));

        assertThat(campos).contains(new Campo("Consulta montada a partir do documento", consultaNoLimite));
    }
}
