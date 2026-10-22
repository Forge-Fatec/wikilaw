package forge.wikilaw.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.entity.FonteDados;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FonteDadosRepositoryTest {

    @Autowired
    private FonteDadosRepository repository;

    @Test
    void findsFonteDadosBySigla() {
        FonteDados datajud = new FonteDados();
        datajud.setSigla("DATAJUD");
        datajud.setNome("API Pública DataJud");
        datajud.setTipoFonte("API");
        repository.saveAndFlush(datajud);

        assertThat(repository.findBySigla("DATAJUD"))
                .get()
                .extracting(FonteDados::getNome)
                .isEqualTo("API Pública DataJud");
    }

    @Test
    void returnsEmptyWhenSiglaDoesNotExist() {
        assertThat(repository.findBySigla("INEXISTENTE")).isEmpty();
    }
}
