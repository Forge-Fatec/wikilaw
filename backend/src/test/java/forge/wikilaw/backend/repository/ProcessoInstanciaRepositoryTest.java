package forge.wikilaw.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.entity.FonteDados;
import forge.wikilaw.backend.entity.Processo;
import forge.wikilaw.backend.entity.ProcessoInstancia;
import forge.wikilaw.backend.entity.Tribunal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProcessoInstanciaRepositoryTest {

    @Autowired
    private ProcessoInstanciaRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findsInstanciaByFonteAndIdentificadorExterno() {
        FonteDados datajud = fonte("DATAJUD");
        ProcessoInstancia instancia = repository.saveAndFlush(instancia(datajud, "TJSP_G1_123"));

        assertThat(repository.findByFonteIdAndIdentificadorExterno(datajud.getId(), "TJSP_G1_123"))
                .get()
                .extracting(ProcessoInstancia::getId)
                .isEqualTo(instancia.getId());
    }

    @Test
    void doesNotMatchSameIdentificadorFromAnotherFonte() {
        FonteDados datajud = fonte("DATAJUD");
        FonteDados outra = fonte("OUTRA");
        repository.saveAndFlush(instancia(datajud, "TJSP_G1_123"));

        assertThat(repository.findByFonteIdAndIdentificadorExterno(outra.getId(), "TJSP_G1_123")).isEmpty();
    }

    private FonteDados fonte(String sigla) {
        FonteDados fonte = new FonteDados();
        fonte.setSigla(sigla);
        fonte.setNome("Fonte " + sigla);
        fonte.setTipoFonte("API");
        return entityManager.persist(fonte);
    }

    private ProcessoInstancia instancia(FonteDados fonte, String identificadorExterno) {
        Processo processo = new Processo();
        processo.setNumeroCnj("40017037420268260360");
        Tribunal tribunal = new Tribunal();
        tribunal.setSigla("TJSP");
        tribunal.setNome("Tribunal de Justiça de São Paulo");

        ProcessoInstancia instancia = new ProcessoInstancia();
        instancia.setProcesso(entityManager.persist(processo));
        instancia.setTribunal(entityManager.persist(tribunal));
        instancia.setFonte(fonte);
        instancia.setIdentificadorExterno(identificadorExterno);
        return instancia;
    }
}
