package forge.wikilaw.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import forge.wikilaw.backend.entity.ImportJob;
import forge.wikilaw.backend.entity.ImportJob.Status;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ImportJobRepositoryTest {

    private static final OffsetDateTime BASE = OffsetDateTime.parse("2026-10-01T10:00:00Z");

    @Autowired
    private ImportJobRepository repository;

    @Test
    void findsLatestIncrementalBaseJobByEscopoAndStatus() {
        ImportJob antigo = job("TJSP", Status.CONCLUIDA, 1);
        antigo.setIncrementalBase(true);
        ImportJob recente = job("TJSP", Status.CONCLUIDA, 2);
        recente.setIncrementalBase(true);
        ImportJob semBase = job("TJSP", Status.CONCLUIDA, 3);
        ImportJob outroStatus = job("TJSP", Status.FALHA, 4);
        outroStatus.setIncrementalBase(true);
        ImportJob outroEscopo = job("TJRJ", Status.CONCLUIDA, 5);
        outroEscopo.setIncrementalBase(true);
        repository.saveAllAndFlush(List.of(antigo, recente, semBase, outroStatus, outroEscopo));

        assertThat(repository.findTopByEscopoAndStatusAndIncrementalBaseTrueOrderByAtualizadoEmDesc("TJSP", Status.CONCLUIDA))
                .get()
                .extracting(ImportJob::getId)
                .isEqualTo(recente.getId());
    }

    private ImportJob job(String escopo, Status status, int minutos) {
        ImportJob job = new ImportJob();
        job.setFonte("DATAJUD");
        job.setEscopo(escopo);
        job.setStatus(status);
        job.setRequisicao("{}");
        job.setCriadoEm(BASE.plusMinutes(minutos));
        job.setAtualizadoEm(BASE.plusMinutes(minutos));
        return job;
    }
}
