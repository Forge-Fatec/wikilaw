package forge.wikilaw.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.entity.TrechoSemantico;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TrechoSemanticoRepositoryTest {

    private static final long DOCUMENTO = 10L;
    private static final long OUTRO_DOCUMENTO = 20L;

    @Autowired
    private TrechoSemanticoRepository repository;

    @Test
    void findsTrechosOfDocumentoOrderedByOrdem() {
        repository.save(trecho(DOCUMENTO, 2));
        repository.save(trecho(DOCUMENTO, 0));
        repository.save(trecho(OUTRO_DOCUMENTO, 0));
        repository.save(trecho(DOCUMENTO, 1));
        repository.flush();

        assertThat(repository.findByIdDocumentoSemanticoOrderByOrdem(DOCUMENTO))
                .extracting(TrechoSemantico::getOrdem)
                .containsExactly(0, 1, 2);
    }

    @Test
    void deletesOnlyTrechosOfGivenDocumento() {
        repository.save(trecho(DOCUMENTO, 0));
        repository.save(trecho(DOCUMENTO, 1));
        repository.save(trecho(OUTRO_DOCUMENTO, 0));
        repository.flush();

        repository.deleteByIdDocumentoSemantico(DOCUMENTO);
        repository.flush();

        assertThat(repository.findAll())
                .extracting(TrechoSemantico::getIdDocumentoSemantico)
                .containsExactly(OUTRO_DOCUMENTO);
    }

    @Test
    void countsTrechosOfGivenDocumento() {
        repository.save(trecho(DOCUMENTO, 0));
        repository.save(trecho(DOCUMENTO, 1));
        repository.save(trecho(OUTRO_DOCUMENTO, 0));
        repository.flush();

        assertThat(repository.countByIdDocumentoSemantico(DOCUMENTO)).isEqualTo(2);
        assertThat(repository.countByIdDocumentoSemantico(99L)).isZero();
    }

    private TrechoSemantico trecho(long idDocumento, int ordem) {
        TrechoSemantico trecho = new TrechoSemantico();
        trecho.setIdDocumentoSemantico(idDocumento);
        trecho.setOrdem(ordem);
        trecho.setSecao("EMENTA");
        trecho.setTexto("Trecho " + ordem);
        trecho.setTextoParaEmbedding("Trecho " + ordem);
        trecho.setQuantidadeTokens(2);
        trecho.setHashTexto("hash-" + idDocumento + "-" + ordem);
        return trecho;
    }
}
