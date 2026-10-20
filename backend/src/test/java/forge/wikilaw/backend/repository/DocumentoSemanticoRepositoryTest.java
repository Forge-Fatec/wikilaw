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

    @Test
    void findsDocumentoSemanticoByIdDocumentoDoutrinario() {
        DocumentoSemantico semantico = repository.saveAndFlush(semantico(Categoria.DOUTRINA, 10L, VERSAO, Status.PRONTO));

        assertThat(repository.findByIdDocumentoDoutrinario(10L))
                .get()
                .extracting(DocumentoSemantico::getId)
                .isEqualTo(semantico.getId());
        assertThat(repository.findByIdDocumentoDoutrinario(99L)).isEmpty();
    }

    @Test
    void findsDecisoesPendentesForVersao() {
        Long semSemantico = persist(new DecisaoJudicial(), "D1", true);
        Long pronta = persist(new DecisaoJudicial(), "D2", true);
        Long comErro = persist(new DecisaoJudicial(), "D3", true);
        Long versaoAntiga = persist(new DecisaoJudicial(), "D4", true);
        Long emAndamento = persist(new DecisaoJudicial(), "D5", true);
        persist(new DecisaoJudicial(), "D6", false);
        repository.save(semantico(Categoria.JURISPRUDENCIA, pronta, VERSAO, Status.PRONTO));
        repository.save(semantico(Categoria.JURISPRUDENCIA, comErro, VERSAO, Status.ERRO));
        repository.save(semantico(Categoria.JURISPRUDENCIA, versaoAntiga, "v1", Status.PRONTO));
        repository.save(semantico(Categoria.JURISPRUDENCIA, emAndamento, VERSAO, Status.PROCESSANDO));
        repository.flush();

        assertThat(repository.findDecisoesPendentes(VERSAO, 10))
                .containsExactly(semSemantico, versaoAntiga, emAndamento);
        assertThat(repository.findDecisoesPendentes(VERSAO, 2))
                .containsExactly(semSemantico, versaoAntiga);
    }

    @Test
    void findsPrecedentesPendentesForVersao() {
        Long semSemantico = persist(new Precedente(), "P1", true);
        Long pronto = persist(new Precedente(), "P2", true);
        Long comErro = persist(new Precedente(), "P3", true);
        Long versaoAntiga = persist(new Precedente(), "P4", true);
        Long emAndamento = persist(new Precedente(), "P5", true);
        persist(new Precedente(), "P6", false);
        repository.save(semantico(Categoria.PRECEDENTE, pronto, VERSAO, Status.PRONTO));
        repository.save(semantico(Categoria.PRECEDENTE, comErro, VERSAO, Status.ERRO));
        repository.save(semantico(Categoria.PRECEDENTE, versaoAntiga, "v1", Status.PRONTO));
        repository.save(semantico(Categoria.PRECEDENTE, emAndamento, VERSAO, Status.PROCESSANDO));
        repository.flush();

        assertThat(repository.findPrecedentesPendentes(VERSAO, 10))
                .containsExactly(semSemantico, versaoAntiga, emAndamento);
        assertThat(repository.findPrecedentesPendentes(VERSAO, 2))
                .containsExactly(semSemantico, versaoAntiga);
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
