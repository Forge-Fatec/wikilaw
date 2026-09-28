package forge.wikilaw.backend.repository;

import forge.wikilaw.backend.entity.AssuntoProcessual;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssuntoProcessualRepository extends JpaRepository<AssuntoProcessual, Long> {
    Optional<AssuntoProcessual> findByCodigoCnj(Long codigoCnj);
}
