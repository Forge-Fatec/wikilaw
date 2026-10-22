package forge.wikilaw.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.entity.FonteDados;
import forge.wikilaw.backend.entity.RegistroBruto;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RegistroBrutoRepositoryTest {

    @Autowired
    private RegistroBrutoRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savesAndFindsRegistroById() {
        FonteDados fonte = new FonteDados();
        fonte.setSigla("DATAJUD");
        fonte.setNome("API Pública DataJud");
        fonte.setTipoFonte("API");

        RegistroBruto registro = new RegistroBruto();
        registro.setFonte(entityManager.persist(fonte));
        registro.setIdentificadorExterno("TJSP_G1_123");
        registro.setFormatoPayload("JSON");
        registro.setDataColeta(OffsetDateTime.now());
        registro.setHashConteudo("a".repeat(64));
        registro.setPayloadTexto("{\"numeroProcesso\":\"123\"}");
        Long id = repository.saveAndFlush(registro).getId();
        entityManager.clear();

        assertThat(repository.findById(id))
                .get()
                .satisfies(encontrado -> {
                    assertThat(encontrado.getIdentificadorExterno()).isEqualTo("TJSP_G1_123");
                    assertThat(encontrado.getFormatoPayload()).isEqualTo("JSON");
                });
    }
}
