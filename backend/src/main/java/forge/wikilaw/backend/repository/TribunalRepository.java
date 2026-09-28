package forge.wikilaw.backend.repository;

import forge.wikilaw.backend.entity.Tribunal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TribunalRepository extends JpaRepository<Tribunal, Long> {
    Optional<Tribunal> findBySigla(String sigla);
}
