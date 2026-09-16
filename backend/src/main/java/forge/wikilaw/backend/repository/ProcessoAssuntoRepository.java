package forge.wikilaw.backend.repository;

import forge.wikilaw.backend.entity.ProcessoAssunto;
import forge.wikilaw.backend.entity.ProcessoAssuntoId;
import forge.wikilaw.backend.entity.ProcessoInstancia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessoAssuntoRepository extends JpaRepository<ProcessoAssunto, ProcessoAssuntoId> {
    long deleteByProcessoInstancia(ProcessoInstancia processoInstancia);
}
