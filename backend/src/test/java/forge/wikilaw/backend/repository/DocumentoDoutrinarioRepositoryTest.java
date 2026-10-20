package forge.wikilaw.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.entity.DocumentoDoutrinario;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DocumentoDoutrinarioRepositoryTest {

    private static final long FONTE_SCIELO = 1L;
    private static final long FONTE_PANGEA = 2L;

    @Autowired
    private DocumentoDoutrinarioRepository repository;

    @Test
    void findsDocumentoByFonteAndIdentificadorExterno() {
        repository.saveAndFlush(documento(FONTE_SCIELO, "ART-1000", true));

        assertThat(repository.findByIdFonteAndIdentificadorExterno(FONTE_SCIELO, "ART-1000"))
                .get()
                .extracting(DocumentoDoutrinario::getTitulo)
                .isEqualTo("Artigo ART-1000");
        assertThat(repository.findByIdFonteAndIdentificadorExterno(FONTE_PANGEA, "ART-1000")).isEmpty();
    }

    private DocumentoDoutrinario documento(long fonte, String identificador, boolean ativo) {
        DocumentoDoutrinario documento = new DocumentoDoutrinario();
        documento.setIdFonte(fonte);
        documento.setIdRegistroBruto(1L);
        documento.setIdentificadorExterno(identificador);
        documento.setTitulo("Artigo " + identificador);
        documento.setAtualizadoEm(OffsetDateTime.now());
        documento.setAtivo(ativo);
        return documento;
    }
}
