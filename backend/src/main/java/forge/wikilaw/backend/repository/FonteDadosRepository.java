package forge.wikilaw.backend.repository;

import forge.wikilaw.backend.entity.FonteDados;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FonteDadosRepository extends JpaRepository<FonteDados, Long> {
    Optional<FonteDados> findBySigla(String sigla);
}
