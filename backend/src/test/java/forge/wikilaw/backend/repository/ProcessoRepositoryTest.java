package forge.wikilaw.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import forge.wikilaw.backend.entity.Processo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProcessoRepositoryTest {

    @Autowired
    private ProcessoRepository repository;

    @Test
    void enforcesUniqueCnjNumberAtPersistenceLayer() {
        Processo first = new Processo();
        first.setNumeroCnj("40017037420268260360");
        repository.saveAndFlush(first);

        assertThat(repository.findByNumeroCnj("40017037420268260360")).isPresent();

        Processo duplicate = new Processo();
        duplicate.setNumeroCnj("40017037420268260360");
        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
