package forge.wikilaw.backend.repository;

import forge.wikilaw.backend.entity.MovimentoProcessual;
import forge.wikilaw.backend.entity.ProcessoInstancia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimentoProcessualRepository extends JpaRepository<MovimentoProcessual, Long> {
    long deleteByProcessoInstancia(ProcessoInstancia processoInstancia);
}
