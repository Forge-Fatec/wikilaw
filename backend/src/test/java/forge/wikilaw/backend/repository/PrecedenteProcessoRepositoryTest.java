package forge.wikilaw.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.entity.PrecedenteProcesso;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PrecedenteProcessoRepositoryTest {

    private static final long PRECEDENTE = 10L;
    private static final long OUTRO_PRECEDENTE = 20L;

    @Autowired
    private PrecedenteProcessoRepository repository;

    @Test
    void findsProcessosOfPrecedenteOrderedByNumeroRegistro() {
        repository.save(vinculo(PRECEDENTE, "RE 300"));
        repository.save(vinculo(PRECEDENTE, "RE 100"));
        repository.save(vinculo(OUTRO_PRECEDENTE, "RE 050"));
        repository.save(vinculo(PRECEDENTE, "RE 200"));
        repository.flush();

        assertThat(repository.findByIdPrecedenteOrderByNumeroRegistro(PRECEDENTE))
                .extracting(PrecedenteProcesso::getNumeroRegistro)
                .containsExactly("RE 100", "RE 200", "RE 300");
    }

    @Test
    void deletesOnlyProcessosOfGivenPrecedente() {
        repository.save(vinculo(PRECEDENTE, "RE 100"));
        repository.save(vinculo(PRECEDENTE, "RE 200"));
        repository.save(vinculo(OUTRO_PRECEDENTE, "RE 050"));
        repository.flush();

        repository.deleteByIdPrecedente(PRECEDENTE);
        repository.flush();

        assertThat(repository.findAll())
                .extracting(PrecedenteProcesso::getIdPrecedente)
                .containsExactly(OUTRO_PRECEDENTE);
    }

    private PrecedenteProcesso vinculo(long idPrecedente, String numeroRegistro) {
        PrecedenteProcesso vinculo = new PrecedenteProcesso();
        vinculo.setIdPrecedente(idPrecedente);
        vinculo.setIdRegistroBruto(1L);
        vinculo.setNumeroRegistro(numeroRegistro);
        return vinculo;
    }
}
