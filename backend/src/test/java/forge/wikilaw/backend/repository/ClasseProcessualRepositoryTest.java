package forge.wikilaw.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.entity.ClasseProcessual;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ClasseProcessualRepositoryTest {

    @Autowired
    private ClasseProcessualRepository repository;

    @Test
    void findsClasseByCodigoCnj() {
        ClasseProcessual classe = new ClasseProcessual();
        classe.setCodigoCnj(436L);
        classe.setNome("Procedimento do Juizado Especial Cível");
        repository.saveAndFlush(classe);

        assertThat(repository.findByCodigoCnj(436L))
                .get()
                .extracting(ClasseProcessual::getNome)
                .isEqualTo("Procedimento do Juizado Especial Cível");
    }

    @Test
    void returnsEmptyWhenCodigoCnjDoesNotExist() {
        assertThat(repository.findByCodigoCnj(999999L)).isEmpty();
    }
}
