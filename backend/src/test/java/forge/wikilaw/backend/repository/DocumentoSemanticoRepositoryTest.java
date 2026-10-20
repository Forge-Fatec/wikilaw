package forge.wikilaw.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.entity.DecisaoJudicial;
import forge.wikilaw.backend.entity.DocumentoBase;
import forge.wikilaw.backend.entity.DocumentoDoutrinario;
import forge.wikilaw.backend.entity.DocumentoSemantico;
import forge.wikilaw.backend.entity.DocumentoSemantico.Categoria;
import forge.wikilaw.backend.entity.DocumentoSemantico.Status;
import forge.wikilaw.backend.entity.Precedente;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DocumentoSemanticoRepositoryTest {

    private static final String VERSAO = "v2";

    @Autowired
    private DocumentoSemanticoRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findsDocumentoSemanticoByIdDecisao() {
        DocumentoSemantico semantico = repository.saveAndFlush(semantico(Categoria.JURISPRUDENCIA, 10L, VERSAO, Status.PRONTO));

        assertThat(repository.findByIdDecisao(10L))
                .get()
                .extracting(DocumentoSemantico::getId)
                .isEqualTo(semantico.getId());
        assertThat(repository.findByIdDecisao(99L)).isEmpty();
    }

    @Test
    void findsDocumentoSemanticoByIdPrecedente() {
        DocumentoSemantico semantico = repository.saveAndFlush(semantico(Categoria.PRECEDENTE, 10L, VERSAO, Status.PRONTO));

        assertThat(repository.findByIdPrecedente(10L))
                .get()
                .extracting(DocumentoSemantico::getId)
                .isEqualTo(semantico.getId());
        assertThat(repository.findByIdPrecedente(99L)).isEmpty();
    }

    private Long persist(DocumentoBase documento, String identificador, boolean ativo) {
        documento.setIdFonte(1L);
        documento.setIdRegistroBruto(1L);
        documento.setIdentificadorExterno(identificador);
        documento.setTitulo("Documento " + identificador);
        documento.setAtualizadoEm(OffsetDateTime.now());
        documento.setAtivo(ativo);
        return entityManager.persistAndGetId(documento, Long.class);
    }

    private DocumentoSemantico semantico(Categoria categoria, Long idOrigem, String versao, Status status) {
        DocumentoSemantico semantico = new DocumentoSemantico();
        semantico.setCategoria(categoria);
        switch (categoria) {
            case JURISPRUDENCIA -> semantico.setIdDecisao(idOrigem);
            case PRECEDENTE -> semantico.setIdPrecedente(idOrigem);
            case DOUTRINA -> semantico.setIdDocumentoDoutrinario(idOrigem);
        }
        semantico.setVersaoPreparacao(versao);
        semantico.setStatus(status);
        semantico.setCriadoEm(OffsetDateTime.now());
        semantico.setAtualizadoEm(OffsetDateTime.now());
        return semantico;
    }
}
