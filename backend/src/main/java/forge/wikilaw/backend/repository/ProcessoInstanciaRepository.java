package forge.wikilaw.backend.repository;

import forge.wikilaw.backend.entity.ProcessoInstancia;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessoInstanciaRepository extends JpaRepository<ProcessoInstancia, Long> {
    Optional<ProcessoInstancia> findByFonteIdAndIdentificadorExterno(Long fonteId, String identificadorExterno);
}
