package forge.wikilaw.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.entity.AssuntoProcessual;
import forge.wikilaw.backend.entity.FonteDados;
import forge.wikilaw.backend.entity.Processo;
import forge.wikilaw.backend.entity.ProcessoAssunto;
import forge.wikilaw.backend.entity.ProcessoAssuntoId;
import forge.wikilaw.backend.entity.ProcessoInstancia;
import forge.wikilaw.backend.entity.Tribunal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProcessoAssuntoRepositoryTest {

    @Autowired
    private ProcessoAssuntoRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void deletesOnlyAssuntosOfGivenInstancia() {
        FonteDados fonte = fonte();
        Tribunal tribunal = tribunal();
        ProcessoInstancia alvo = instancia(fonte, tribunal, "40017037420268260360");
        ProcessoInstancia outra = instancia(fonte, tribunal, "10000010020268260100");
        AssuntoProcessual danoMoral = assunto(7780L, "Indenização por Dano Moral");
        AssuntoProcessual danoMaterial = assunto(7779L, "Indenização por Dano Material");
        vincular(alvo, danoMoral, true);
        vincular(alvo, danoMaterial, false);
        vincular(outra, danoMoral, true);
        entityManager.flush();

        long removidos = repository.deleteByProcessoInstancia(alvo);

        assertThat(removidos).isEqualTo(2);
        assertThat(repository.findAll())
                .extracting(ProcessoAssunto::getId)
                .containsExactly(new ProcessoAssuntoId(outra.getId(), danoMoral.getId()));
    }

    private FonteDados fonte() {
        FonteDados fonte = new FonteDados();
        fonte.setSigla("DATAJUD");
        fonte.setNome("API Pública DataJud");
        fonte.setTipoFonte("API");
        return entityManager.persist(fonte);
    }

    private Tribunal tribunal() {
        Tribunal tribunal = new Tribunal();
        tribunal.setSigla("TJSP");
        tribunal.setNome("Tribunal de Justiça de São Paulo");
        return entityManager.persist(tribunal);
    }

    private ProcessoInstancia instancia(FonteDados fonte, Tribunal tribunal, String numeroCnj) {
        Processo processo = new Processo();
        processo.setNumeroCnj(numeroCnj);

        ProcessoInstancia instancia = new ProcessoInstancia();
        instancia.setProcesso(entityManager.persist(processo));
        instancia.setFonte(fonte);
        instancia.setTribunal(tribunal);
        instancia.setIdentificadorExterno("TJSP_G1_" + numeroCnj);
        return entityManager.persist(instancia);
    }

    private AssuntoProcessual assunto(Long codigoCnj, String nome) {
        AssuntoProcessual assunto = new AssuntoProcessual();
        assunto.setCodigoCnj(codigoCnj);
        assunto.setNome(nome);
        return entityManager.persist(assunto);
    }

    private void vincular(ProcessoInstancia instancia, AssuntoProcessual assunto, boolean principal) {
        ProcessoAssunto vinculo = new ProcessoAssunto();
        vinculo.setId(new ProcessoAssuntoId(instancia.getId(), assunto.getId()));
        vinculo.setProcessoInstancia(instancia);
        vinculo.setAssuntoProcessual(assunto);
        vinculo.setPrincipal(principal);
        entityManager.persist(vinculo);
    }
}
