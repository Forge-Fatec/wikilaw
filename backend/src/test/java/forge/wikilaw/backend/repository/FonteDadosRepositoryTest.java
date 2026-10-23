package forge.wikilaw.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.entity.FonteDados;
import java.util.List;
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

    @Test
    void listsActiveSourcesExceptGivenTypeOrderedByName() {
        salvar("ZZ_DOUTRINA", "Zz Teste Doutrina", "DOUTRINA", true);
        salvar("ZZ_JURIS", "Zz Teste Acórdãos", "JURISPRUDENCIA", true);
        salvar("ZZ_PROC", "Zz Teste Processual", "PROCESSUAL", true);
        salvar("ZZ_INATIVA", "Zz Teste Inativa", "JURISPRUDENCIA", false);

        List<String> siglas = repository.findByAtivoTrueAndTipoFonteNotOrderByNomeAsc("PROCESSUAL").stream()
                .map(FonteDados::getSigla)
                .filter(sigla -> sigla.startsWith("ZZ_"))
                .toList();

        assertThat(siglas).containsExactly("ZZ_JURIS", "ZZ_DOUTRINA");
    }

    private void salvar(String sigla, String nome, String tipo, boolean ativo) {
        FonteDados fonte = new FonteDados();
        fonte.setSigla(sigla);
        fonte.setNome(nome);
        fonte.setTipoFonte(tipo);
        fonte.setAtivo(ativo);
        repository.saveAndFlush(fonte);
    }
}
