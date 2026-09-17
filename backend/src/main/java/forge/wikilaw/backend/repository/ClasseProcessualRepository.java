package forge.wikilaw.backend.repository;

import forge.wikilaw.backend.entity.ClasseProcessual;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClasseProcessualRepository extends JpaRepository<ClasseProcessual, Long> {
    Optional<ClasseProcessual> findByCodigoCnj(Long codigoCnj);
}
