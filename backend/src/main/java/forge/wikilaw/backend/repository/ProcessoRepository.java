package forge.wikilaw.backend.repository;

import forge.wikilaw.backend.entity.Processo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessoRepository extends JpaRepository<Processo, Long> {
    Optional<Processo> findByNumeroCnj(String numeroCnj);
}
