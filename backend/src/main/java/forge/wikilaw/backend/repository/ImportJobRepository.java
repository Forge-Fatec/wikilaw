package forge.wikilaw.backend.repository;

import forge.wikilaw.backend.entity.ImportJob;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.*;

public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {
    Optional<ImportJob> findTopByEscopoAndStatusAndIncrementalBaseTrueOrderByAtualizadoEmDesc(String escopo, ImportJob.Status status);
    Optional<ImportJob> findTopByEscopoAndBootstrapCompletoTrueOrderByCriadoEmDesc(String escopo);
    Optional<ImportJob> findFirstByEscopoAndStatusIn(String escopo, List<ImportJob.Status> statuses);
    // The row remains locked until the page checkpoint commits. A crashed worker
    // releases its database lock, so another instance can replay the same page.
    @Query(value = """
            select * from import_job
            where status in ('PENDENTE', 'EM_EXECUCAO')
            order by atualizado_em, id limit 1 for update skip locked
            """, nativeQuery = true)
    Optional<ImportJob> lockNext();

    @Query(value = "select * from import_job where id = :id for update skip locked", nativeQuery = true)
    Optional<ImportJob> lockAvailable(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ImportJob j where j.id = :id")
    Optional<ImportJob> lockById(Long id);
}
