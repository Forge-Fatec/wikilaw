package forge.wikilaw.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.entity.OrgaoJulgador;
import forge.wikilaw.backend.entity.Tribunal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrgaoJulgadorRepositoryTest {

    @Autowired
    private OrgaoJulgadorRepository repository;

    @Autowired
    private TribunalRepository tribunalRepository;

    @Test
    void findsOrgaoByTribunalAndCodigoExterno() {
        Tribunal tjsp = tribunal("TJSP");
        repository.saveAndFlush(orgao(tjsp, "1234", "1ª Vara Cível de São Paulo"));

        assertThat(repository.findByTribunalIdAndCodigoExterno(tjsp.getId(), "1234"))
                .get()
                .extracting(OrgaoJulgador::getNome)
                .isEqualTo("1ª Vara Cível de São Paulo");
    }

    @Test
    void doesNotMatchSameCodigoExternoFromAnotherTribunal() {
        Tribunal tjsp = tribunal("TJSP");
        Tribunal tjrj = tribunal("TJRJ");
        repository.saveAndFlush(orgao(tjsp, "1234", "1ª Vara Cível de São Paulo"));

        assertThat(repository.findByTribunalIdAndCodigoExterno(tjrj.getId(), "1234")).isEmpty();
    }

    private Tribunal tribunal(String sigla) {
        Tribunal tribunal = new Tribunal();
        tribunal.setSigla(sigla);
        tribunal.setNome("Tribunal " + sigla);
        return tribunalRepository.saveAndFlush(tribunal);
    }

    private OrgaoJulgador orgao(Tribunal tribunal, String codigoExterno, String nome) {
        OrgaoJulgador orgao = new OrgaoJulgador();
        orgao.setTribunal(tribunal);
        orgao.setCodigoExterno(codigoExterno);
        orgao.setNome(nome);
        return orgao;
    }
}
