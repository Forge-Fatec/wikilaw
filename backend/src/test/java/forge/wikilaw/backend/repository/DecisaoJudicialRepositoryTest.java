package forge.wikilaw.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.entity.DecisaoJudicial;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DecisaoJudicialRepositoryTest {

    private static final long FONTE_STF = 1L;
    private static final long FONTE_STJ = 2L;

    @Autowired
    private DecisaoJudicialRepository repository;

    @Test
    void findsDecisaoByFonteAndIdentificadorExterno() {
        repository.saveAndFlush(decisao(FONTE_STF, "RE-1000", true));

        assertThat(repository.findByIdFonteAndIdentificadorExterno(FONTE_STF, "RE-1000"))
                .get()
                .extracting(DecisaoJudicial::getTitulo)
                .isEqualTo("Decisão RE-1000");
        assertThat(repository.findByIdFonteAndIdentificadorExterno(FONTE_STJ, "RE-1000")).isEmpty();
    }

    private DecisaoJudicial decisao(long fonte, String identificador, boolean ativo) {
        DecisaoJudicial decisao = new DecisaoJudicial();
        decisao.setIdFonte(fonte);
        decisao.setIdRegistroBruto(1L);
        decisao.setIdentificadorExterno(identificador);
        decisao.setTitulo("Decisão " + identificador);
        decisao.setAtualizadoEm(OffsetDateTime.now());
        decisao.setAtivo(ativo);
        return decisao;
    }
}
