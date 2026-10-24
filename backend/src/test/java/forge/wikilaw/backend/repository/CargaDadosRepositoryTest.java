package forge.wikilaw.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.entity.CargaDados;
import forge.wikilaw.backend.entity.CargaStatus;
import forge.wikilaw.backend.entity.FonteDados;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CargaDadosRepositoryTest {

    @Autowired
    private CargaDadosRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savesAndFindsCargaById() {
        FonteDados fonte = new FonteDados();
        fonte.setSigla("DATAJUD");
        fonte.setNome("API Pública DataJud");
        fonte.setTipoFonte("API");

        CargaDados carga = new CargaDados();
        carga.setFonte(entityManager.persist(fonte));
        carga.setDataInicio(OffsetDateTime.now());
        carga.setStatus(CargaStatus.EM_EXECUCAO);
        carga.setQuantidadeRecebida(100);
        Long id = repository.saveAndFlush(carga).getId();
        entityManager.clear();

        assertThat(repository.findById(id))
                .get()
                .satisfies(encontrada -> {
                    assertThat(encontrada.getStatus()).isEqualTo(CargaStatus.EM_EXECUCAO);
                    assertThat(encontrada.getQuantidadeRecebida()).isEqualTo(100);
                });
    }
}
