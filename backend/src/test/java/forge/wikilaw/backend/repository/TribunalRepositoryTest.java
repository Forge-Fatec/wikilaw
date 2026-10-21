package forge.wikilaw.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.entity.Tribunal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TribunalRepositoryTest {

    @Autowired
    private TribunalRepository repository;

    @Test
    void findsTribunalBySigla() {
        Tribunal tjsp = new Tribunal();
        tjsp.setSigla("TJSP");
        tjsp.setNome("Tribunal de Justiça de São Paulo");
        repository.saveAndFlush(tjsp);

        assertThat(repository.findBySigla("TJSP"))
                .get()
                .extracting(Tribunal::getNome)
                .isEqualTo("Tribunal de Justiça de São Paulo");
    }

    @Test
    void returnsEmptyWhenSiglaDoesNotExist() {
        assertThat(repository.findBySigla("TJXX")).isEmpty();
    }
}
