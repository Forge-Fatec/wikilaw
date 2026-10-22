package forge.wikilaw.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.entity.Precedente;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PrecedenteRepositoryTest {

    private static final long FONTE_STF = 1L;
    private static final long FONTE_STJ = 2L;

    @Autowired
    private PrecedenteRepository repository;

    @Test
    void findsPrecedenteByFonteAndIdentificadorExterno() {
        repository.saveAndFlush(precedente(FONTE_STF, "TEMA-1000", true));

        assertThat(repository.findByIdFonteAndIdentificadorExterno(FONTE_STF, "TEMA-1000"))
                .get()
                .extracting(Precedente::getTitulo)
                .isEqualTo("Precedente TEMA-1000");
        assertThat(repository.findByIdFonteAndIdentificadorExterno(FONTE_STJ, "TEMA-1000")).isEmpty();
    }

    @Test
    void countsOnlyActivePrecedentesOfGivenFonte() {
        repository.save(precedente(FONTE_STF, "TEMA-1000", true));
        repository.save(precedente(FONTE_STF, "TEMA-1001", true));
        repository.save(precedente(FONTE_STF, "TEMA-1002", false));
        repository.save(precedente(FONTE_STJ, "TEMA-2000", true));
        repository.flush();

        assertThat(repository.countByIdFonteAndAtivoTrue(FONTE_STF)).isEqualTo(2);
    }

    private Precedente precedente(long fonte, String identificador, boolean ativo) {
        Precedente precedente = new Precedente();
        precedente.setIdFonte(fonte);
        precedente.setIdRegistroBruto(1L);
        precedente.setIdentificadorExterno(identificador);
        precedente.setTitulo("Precedente " + identificador);
        precedente.setAtualizadoEm(OffsetDateTime.now());
        precedente.setAtivo(ativo);
        return precedente;
    }
}
