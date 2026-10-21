package forge.wikilaw.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.entity.AssuntoProcessual;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AssuntoProcessualRepositoryTest {

    @Autowired
    private AssuntoProcessualRepository repository;

    @Test
    void findsAssuntoByCodigoCnj() {
        AssuntoProcessual assunto = new AssuntoProcessual();
        assunto.setCodigoCnj(7780L);
        assunto.setNome("Indenização por Dano Moral");
        repository.saveAndFlush(assunto);

        assertThat(repository.findByCodigoCnj(7780L))
                .get()
                .extracting(AssuntoProcessual::getNome)
                .isEqualTo("Indenização por Dano Moral");
    }

    @Test
    void returnsEmptyWhenCodigoCnjDoesNotExist() {
        assertThat(repository.findByCodigoCnj(999999L)).isEmpty();
    }
}
